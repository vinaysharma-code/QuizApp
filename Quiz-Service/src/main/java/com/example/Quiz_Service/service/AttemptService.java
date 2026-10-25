package com.example.Quiz_Service.service;

import com.example.Quiz_Service.dto.response.*;
import com.example.Quiz_Service.entity.*;
import com.example.Quiz_Service.repository.*;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttemptService {
  private final AttemptRepo attemptRepo;
  private final UserRepo userRepo;
  private final QuizService quizService;
  private final Clock clock;

  public AttemptService(
      AttemptRepo attemptRepo, UserRepo userRepo, QuizService quizService, Clock clock) {
    this.attemptRepo = attemptRepo;
    this.userRepo = userRepo;
    this.quizService = quizService;
    this.clock = clock;
  }

  @Transactional
  public AttemptResponse start(String quizId) {
    return start(quizId, null);
  }

  @Transactional
  public AttemptResponse start(String quizId, String startKey) {
    if (startKey != null && !startKey.matches("[A-Za-z0-9_-]{8,100}"))
      bad("Invalid Idempotency-Key");
    Quiz quiz = quizService.findPublished(quizId);
    if (quiz.getSnapshots().isEmpty())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz has no published snapshot");
    String student = studentId();
    userRepo
        .findLockedById(student)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    if (startKey != null) {
      var previous = attemptRepo.findByQuizIdAndStudentIdAndStartKey(quizId, student, startKey);
      if (previous.isPresent()) {
        expire(previous.get());
        attemptRepo.flush();
        var response = view(previous.get());
        response.setResumed(true);
        return response;
      }
    }
    java.time.Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    if (quiz.getOpensAt() != null && now.isBefore(quiz.getOpensAt()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Quiz has not opened yet");
    if (quiz.getClosesAt() != null && !now.isBefore(quiz.getClosesAt()))
      throw new ResponseStatusException(HttpStatus.GONE, "Quiz is closed");
    var active =
        attemptRepo.findFirstByQuizIdAndStudentIdAndStatusAndDeadlineAfterOrderByStartedAtDesc(
            quizId, student, Attempt.Status.IN_PROGRESS, now);
    if (active.isPresent()) {
      var response = view(active.get());
      response.setResumed(true);
      return response;
    }
    long used = attemptRepo.countByQuizIdAndStudentId(quizId, student);
    if (used >= quiz.getMaxAttempts())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum attempts reached");
    Attempt attempt = new Attempt();
    attempt.setQuiz(quiz);
    attempt.setStudentId(student);
    attempt.setAttemptNumber((int) used + 1);
    attempt.setStartKey(startKey);
    attempt.setStartedAt(now);
    java.time.Instant deadline = now.plusSeconds(quiz.getDurationSeconds());
    if (quiz.getClosesAt() != null && quiz.getClosesAt().isBefore(deadline))
      deadline = quiz.getClosesAt();
    attempt.setDeadline(deadline);
    return view(attemptRepo.saveAndFlush(attempt));
  }

  @Transactional
  public AttemptResponse saveAnswers(String id, java.util.List<Response> responses, long version) {
    Attempt a = ownedLocked(id);
    expire(a);
    if (a.getStatus() != Attempt.Status.IN_PROGRESS) {
      attemptRepo.flush();
      return view(a);
    }
    if (version != a.getVersion())
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Answers changed in another tab. Reload the attempt before editing.");
    if (responses == null || responses.size() > a.getQuiz().getSnapshots().size())
      bad("Invalid response count");
    var bank =
        a.getQuiz().getSnapshots().stream()
            .collect(Collectors.toMap(SnapshotQuestion::getId, q -> q));
    java.util.Map<String, String> answers = new java.util.HashMap<>();
    for (Response response : responses) {
      if (response == null
          || response.getId() == null
          || !bank.containsKey(response.getId())
          || response.getResponse() == null) bad("Response does not belong to this quiz");
      SnapshotQuestion q = bank.get(response.getId());
      String text = response.getResponse().trim();
      if (!java.util.List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD())
              .contains(text)
          || answers.putIfAbsent(response.getId(), text) != null)
        bad("Invalid or duplicate answer");
    }
    a.setResponses(answers);
    a.setAutosaved(true);
    attemptRepo.flush();
    return view(a);
  }

  @Transactional
  public void finalizeExpired(String id) {
    attemptRepo.findLockedById(id).ifPresent(this::expire);
  }

  @Transactional
  public AttemptResponse get(String id) {
    Attempt a = ownedLocked(id);
    expire(a);
    attemptRepo.flush();
    return view(a);
  }

  @Transactional
  public AttemptResponse submit(String id, List<Response> responses) {
    Attempt a = ownedLocked(id);
    // Lock + local transaction serialize concurrent submissions. Replay returns the stored result.
    if (a.getStatus() != Attempt.Status.IN_PROGRESS) {
      attemptRepo.flush();
      return view(a);
    }
    expire(a);
    if (a.getStatus() != Attempt.Status.IN_PROGRESS) {
      attemptRepo.flush();
      return view(a);
    }
    if (responses == null
        || responses.isEmpty()
        || responses.size() > a.getQuiz().getSnapshots().size())
      bad("Provide 1 to the quiz question-count responses");
    var bank =
        a.getQuiz().getSnapshots().stream()
            .collect(Collectors.toMap(SnapshotQuestion::getId, q -> q));
    Map<String, String> answers = new HashMap<>();
    int score = 0;
    for (Response r : responses) {
      if (r == null || r.getId() == null || !bank.containsKey(r.getId()) || r.getResponse() == null)
        bad("Response does not belong to this quiz");
      String answer = r.getResponse().trim();
      SnapshotQuestion q = bank.get(r.getId());
      if (!List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD()).contains(answer))
        bad("Answer must match an option");
      if (answers.putIfAbsent(r.getId(), answer) != null) bad("Duplicate question response");
      if (answer.equals(q.getRightAnswer())) score++;
    }
    a.setResponses(answers);
    a.setScore(score);
    a.setSubmittedAt(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    a.setStatus(Attempt.Status.SUBMITTED);
    attemptRepo.flush();
    return view(a);
  }

  @Transactional
  public Page<AttemptResponse> history(int page, int size) {
    for (String id : attemptRepo.dueForStudent(studentId(), clock.instant()))
      attemptRepo.findLockedById(id).ifPresent(this::expire);
    attemptRepo.flush();
    // History is read-only with respect to other students. Expiration is derived without mutating
    // rows here.
    return attemptRepo
        .findByStudentIdOrderByStartedAtDesc(studentId(), PageRequest.of(page, size))
        .map(this::view);
  }

  private Attempt ownedLocked(String id) {
    Attempt a =
        attemptRepo
            .findLockedById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found"));
    if (!a.getStudentId().equals(studentId()))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "This attempt belongs to another student");
    return a;
  }

  private String studentId() {
    String email = SecurityContextHolder.getContext().getAuthentication().getName();
    return userRepo
        .findByEmail(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"))
        .getId();
  }

  private void expire(Attempt a) {
    if (a.getStatus() == Attempt.Status.IN_PROGRESS
        && !clock
            .instant()
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            .isBefore(a.getDeadline())) {
      if (a.isAutosaved()) {
        int score = 0;
        for (SnapshotQuestion q : a.getQuiz().getSnapshots())
          if (q.getRightAnswer().equals(a.getResponses().get(q.getId()))) score++;
        a.setScore(score);
        a.setSubmittedAt(a.getDeadline());
        a.setStatus(Attempt.Status.SUBMITTED);
      } else a.setStatus(Attempt.Status.EXPIRED);
    }
  }

  private AttemptResponse view(Attempt a) {
    String status = a.getStatus().name();
    if (a.getStatus() == Attempt.Status.IN_PROGRESS
        && !clock
            .instant()
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            .isBefore(a.getDeadline())) status = "EXPIRED";
    boolean released = a.getStatus() == Attempt.Status.SUBMITTED && resultAvailable(a.getQuiz());
    AttemptResponse result = new AttemptResponse();
    result.setId(a.getId());
    result.setVersion(a.getVersion());
    result.setQuizId(a.getQuiz().getId());
    result.setTitle(a.getQuiz().getTitle());
    result.setStatus(status);
    result.setStartedAt(a.getStartedAt());
    result.setDeadline(a.getDeadline());
    result.setSubmittedAt(a.getSubmittedAt());
    result.setScore(released ? a.getScore() : null);
    result.setPassed(
        released
            ? a.getScore() * 100
                >= a.getQuiz().getPassingPercent() * a.getQuiz().getSnapshots().size()
            : null);
    result.setResultReleased(released);
    result.setAttemptNumber(a.getAttemptNumber());
    result.setTotal(a.getQuiz().getSnapshots().size());
    result.setQuestions(a.getQuiz().getSnapshots().stream().map(StudentQuestion::from).toList());
    result.setResponses(Map.copyOf(a.getResponses()));
    return result;
  }

  private boolean resultAvailable(Quiz q) {
    java.time.Instant now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return switch (q.getResultReleasePolicy()) {
      case IMMEDIATE -> true;
      case MANUAL -> q.isResultsReleased();
      case AFTER_CLOSE -> q.getClosesAt() != null && !now.isBefore(q.getClosesAt());
      case AT_TIME -> q.getResultsReleaseAt() != null && !now.isBefore(q.getResultsReleaseAt());
    };
  }

  private void bad(String message) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
