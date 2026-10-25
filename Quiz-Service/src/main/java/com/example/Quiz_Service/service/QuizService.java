package com.example.Quiz_Service.service;

import com.example.Quiz_Service.dto.*;
import com.example.Quiz_Service.dto.request.DraftQuizRequest;
import com.example.Quiz_Service.dto.response.*;
import com.example.Quiz_Service.entity.*;
import com.example.Quiz_Service.enums.ResultReleasePolicy;
import com.example.Quiz_Service.feign.QuizInterface;
import com.example.Quiz_Service.repository.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizService {
  private final QuizRepo quizRepo;
  private final QuizInterface quizInterface;
  private final Clock clock;
  private final UserRepo userRepo;

  public QuizService(
      QuizRepo quizRepo, QuizInterface quizInterface, Clock clock, UserRepo userRepo) {
    this.quizRepo = quizRepo;
    this.quizInterface = quizInterface;
    this.clock = clock;
    this.userRepo = userRepo;
  }

  // Compatibility route: immediately publishes a random category selection under the current owner.
  public String createQuiz(QuizDto dto) {
    var rows = quizInterface.snapshot(dto.getCategoryName(), dto.getNoQ());
    checkSnapshot(rows, dto.getNoQ());
    Quiz q = new Quiz();
    q.setTitle(dto.getTitle());
    q.setDurationSeconds(dto.getDurationSeconds());
    q.setOwnerId(currentUserId());
    q.setQuestions(rows.stream().map(QuestionSnapshotResponse::getId).toList());
    applySnapshot(q, rows);
    return quizRepo.saveAndFlush(q).getId();
  }

  @Transactional
  public DraftQuizResponse createDraft(DraftQuizRequest request) {
    validate(request);
    Quiz q = new Quiz();
    q.setOwnerId(currentUserId());
    applyDraft(q, request);
    quizRepo.saveAndFlush(q);
    return draftView(q, List.of());
  }

  @Transactional
  public DraftQuizResponse updateDraft(String id, DraftQuizRequest request) {
    validate(request);
    Quiz q = ownedLocked(id);
    if (q.isPublished())
      fail(
          HttpStatus.CONFLICT,
          "Published content and assessment rules are immutable; create a new draft");
    applyDraft(q, request);
    return draftView(q, List.of());
  }

  @Transactional(readOnly = true)
  public DraftQuizResponse getDraft(String id) {
    return draftView(owned(id), List.of());
  }

  @Transactional(readOnly = true)
  public DraftQuizResponse preview(String id) {
    Quiz q = owned(id);
    var rows =
        q.isPublished()
            ? q.getSnapshots().stream().map(StudentQuestion::from).toList()
            : quizInterface.selected(q.getQuestions()).stream()
                .map(
                    x ->
                        new StudentQuestion(
                            x.getId(),
                            x.getQuestionTitle(),
                            x.getOptionA(),
                            x.getOptionB(),
                            x.getOptionC(),
                            x.getOptionD()))
                .toList();
    return draftView(q, rows);
  }

  @Transactional
  public QuizSummary publish(String id) {
    Quiz q = ownedLocked(id);
    if (q.isPublished()) return summary(q);
    if (q.getClosesAt() != null && !now().isBefore(q.getClosesAt()))
      fail(HttpStatus.BAD_REQUEST, "Closing time must be in the future at publication");
    var rows = quizInterface.selected(q.getQuestions());
    checkSnapshot(rows, q.getQuestions().size());
    if (!rows.stream().map(QuestionSnapshotResponse::getId).toList().equals(q.getQuestions()))
      fail(HttpStatus.SERVICE_UNAVAILABLE, "Question snapshot order does not match draft");
    applySnapshot(q, rows);
    return summary(q);
  }

  @Transactional
  public QuizSummary releaseResults(String id) {
    Quiz q = ownedLocked(id);
    if (!q.isPublished() || q.getResultReleasePolicy() != ResultReleasePolicy.MANUAL)
      fail(HttpStatus.CONFLICT, "Only a published MANUAL-policy quiz can be released explicitly");
    q.setResultsReleased(true);
    return summary(q);
  }

  @Transactional(readOnly = true)
  public Page<QuizSummary> mine(int page, int size) {
    return quizRepo
        .findByOwnerIdOrderByIdDesc(currentUserId(), PageRequest.of(page, size))
        .map(this::summary);
  }

  @Transactional(readOnly = true)
  public Page<QuizSummary> published(int page, int size, String search) {
    if (search.length() > 100) fail(HttpStatus.BAD_REQUEST, "Search is too long");
    return quizRepo.searchPublished(search, PageRequest.of(page, size)).map(this::summary);
  }

  @Transactional(readOnly = true)
  public Page<QuizSummary> published(int page, int size) {
    return quizRepo
        .findByPublishedTrueOrderByPublishedAtDesc(PageRequest.of(page, size))
        .map(this::summary);
  }

  @Transactional(readOnly = true)
  public List<StudentQuestion> getQuizQuestion(String id) {
    return owned(id).getSnapshots().stream().map(StudentQuestion::from).toList();
  }

  @Transactional(readOnly = true)
  public QuizSummary publishedQuiz(String id) {
    return summary(findPublished(id));
  }

  @Transactional(readOnly = true)
  public Quiz findPublished(String id) {
    return quizRepo
        .findById(id)
        .filter(Quiz::isPublished)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Published quiz not found"));
  }

  public Quiz owned(String id) {
    Quiz q =
        quizRepo
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
    checkOwner(q);
    return q;
  }

  private Quiz ownedLocked(String id) {
    Quiz q =
        quizRepo
            .findLockedById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz not found"));
    checkOwner(q);
    return q;
  }

  private void checkOwner(Quiz q) {
    if (!Objects.equals(q.getOwnerId(), currentUserId()))
      fail(
          HttpStatus.FORBIDDEN,
          "This quiz belongs to another administrator or has no assigned owner");
  }

  private String currentUserId() {
    return userRepo
        .findByEmail(SecurityContextHolder.getContext().getAuthentication().getName())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
        .getId();
  }

  private void validate(DraftQuizRequest d) {
    if (d.getQuestionIds() == null
        || d.getQuestionIds().isEmpty()
        || d.getQuestionIds().size() > 50
        || d.getQuestionIds().stream().anyMatch(x -> x == null || x.isBlank())
        || new HashSet<>(d.getQuestionIds()).size() != d.getQuestionIds().size())
      fail(HttpStatus.BAD_REQUEST, "Choose 1..50 distinct question IDs");
    if (d.getOpensAt() != null
        && d.getClosesAt() != null
        && !d.getOpensAt().isBefore(d.getClosesAt()))
      fail(HttpStatus.BAD_REQUEST, "Opening time must precede closing time");
    if (d.getResultReleasePolicy() == ResultReleasePolicy.AFTER_CLOSE && d.getClosesAt() == null)
      fail(HttpStatus.BAD_REQUEST, "AFTER_CLOSE requires a closing time");
    if (d.getResultReleasePolicy() == ResultReleasePolicy.AT_TIME
        && d.getResultsReleaseAt() == null)
      fail(HttpStatus.BAD_REQUEST, "AT_TIME requires resultsReleaseAt");
  }

  private void applyDraft(Quiz q, DraftQuizRequest d) {
    q.setTitle(d.getTitle());
    q.setQuestionCount(d.getQuestionIds().size());
    q.setDurationSeconds(d.getDurationSeconds());
    q.setQuestions(new ArrayList<>(d.getQuestionIds()));
    q.setOpensAt(d.getOpensAt());
    q.setClosesAt(d.getClosesAt());
    q.setMaxAttempts(d.getMaxAttempts());
    q.setPassingPercent(d.getPassingPercent());
    q.setResultReleasePolicy(d.getResultReleasePolicy());
    q.setResultsReleaseAt(d.getResultsReleaseAt());
  }

  private void applySnapshot(Quiz q, List<QuestionSnapshotResponse> rows) {
    q.setQuestionCount(rows.size());
    q.setSnapshots(
        new ArrayList<>(
            rows.stream()
                .map(
                    x ->
                        new SnapshotQuestion(
                            x.getId(),
                            x.getQuestionTitle(),
                            x.getOptionA(),
                            x.getOptionB(),
                            x.getOptionC(),
                            x.getOptionD(),
                            x.getRightAnswer()))
                .toList()));
    q.setPublished(true);
    q.setPublishedAt(now());
  }

  private void checkSnapshot(List<QuestionSnapshotResponse> rows, int count) {
    if (rows == null
        || rows.size() != count
        || rows.stream().anyMatch(x -> x == null || x.getId() == null || x.getRightAnswer() == null)
        || rows.stream().map(QuestionSnapshotResponse::getId).distinct().count() != count)
      fail(HttpStatus.SERVICE_UNAVAILABLE, "Question bank returned an incomplete snapshot");
  }

  private DraftQuizResponse draftView(Quiz q, List<StudentQuestion> preview) {
    return new DraftQuizResponse(summary(q), List.copyOf(q.getQuestions()), preview);
  }

  public QuizSummary summary(Quiz q) {
    QuizSummary s = new QuizSummary();
    s.setId(q.getId());
    s.setTitle(q.getTitle());
    s.setDurationSeconds(q.getDurationSeconds());
    s.setQuestionCount(q.getQuestionCount());
    s.setPublished(q.isPublished());
    s.setPublishedAt(q.getPublishedAt());
    s.setOpensAt(q.getOpensAt());
    s.setClosesAt(q.getClosesAt());
    s.setMaxAttempts(q.getMaxAttempts());
    s.setPassingPercent(q.getPassingPercent());
    s.setResultReleasePolicy(q.getResultReleasePolicy().name());
    s.setResultsReleaseAt(q.getResultsReleaseAt());
    s.setResultsReleased(q.isResultsReleased());
    s.setOpenNow(
        q.isPublished()
            && (q.getOpensAt() == null || !now().isBefore(q.getOpensAt()))
            && (q.getClosesAt() == null || now().isBefore(q.getClosesAt())));
    return s;
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private void fail(HttpStatus status, String msg) {
    throw new ResponseStatusException(status, msg);
  }
}
