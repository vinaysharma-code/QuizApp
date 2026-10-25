package com.example.Quiz_Service.service;

import com.example.Quiz_Service.dto.request.ReportFilter;
import com.example.Quiz_Service.dto.response.*;
import com.example.Quiz_Service.entity.*;
import com.example.Quiz_Service.repository.AttemptRepo;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminReportService {
  private final QuizService quizService;
  private final AttemptRepo attemptRepo;
  private final Clock clock;

  public AdminReportService(QuizService quizService, AttemptRepo attemptRepo, Clock clock) {
    this.quizService = quizService;
    this.attemptRepo = attemptRepo;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<AdminAttemptRow> attempts(String id, ReportFilter filter, int page, int size) {
    Quiz q = quizService.owned(id);
    Instant now = clock.instant();
    return attemptRepo
        .findAll(
            spec(q, filter, now),
            PageRequest.of(
                page, size, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))))
        .map(a -> row(a, q, now));
  }

  @Transactional(readOnly = true)
  public QuizStatistics statistics(String id) {
    Quiz q = quizService.owned(id);
    Instant now = clock.instant();
    QuizStatistics s = new QuizStatistics();
    s.setQuizId(id);
    s.setTitle(q.getTitle());
    s.setTotalMarks(q.getSnapshots().size());
    s.setTotalAttempts(attemptRepo.count(spec(q, new ReportFilter(), now)));
    s.setUniqueParticipants(attemptRepo.countParticipants(id));
    s.setSubmittedAttempts(countStatus(q, "SUBMITTED", now));
    s.setExpiredAttempts(countStatus(q, "EXPIRED", now));
    s.setInProgressAttempts(countStatus(q, "IN_PROGRESS", now));
    s.setAverageScore(attemptRepo.averageScore(id));
    s.setHighestScore(attemptRepo.highestScore(id));
    s.setPassedAttempts(
        attemptRepo.passedCount(id, q.getPassingPercent() * q.getSnapshots().size()));
    return s;
  }

  private long countStatus(Quiz q, String status, Instant now) {
    ReportFilter f = new ReportFilter();
    f.setStatus(status);
    return attemptRepo.count(spec(q, f, now));
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public String exportCsv(String id, ReportFilter filter) {
    Quiz q = quizService.owned(id);
    Instant now = clock.instant();
    Specification<Attempt> specification = spec(q, filter, now);
    long count = attemptRepo.count(specification);
    if (count > 10000)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "CSV export is limited to 10000 attempts; narrow the filters");
    StringBuilder csv =
        new StringBuilder(
            "attempt_id,student_name,student_email,status,attempt_number,score,total,passed,started_at,submitted_at,time_taken_seconds\r\n");
    for (int page = 0; page * 1000 < count; page++) {
      var rows =
          attemptRepo.findAll(
              specification,
              PageRequest.of(
                  page, 1000, Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))));
      for (Attempt a : rows) {
        AdminAttemptRow row = row(a, q, now);
        Object[] cells = {
          row.getId(),
          row.getStudentName(),
          row.getStudentEmail(),
          row.getStatus(),
          row.getAttemptNumber(),
          row.getScore(),
          row.getTotal(),
          row.getPassed(),
          row.getStartedAt(),
          row.getSubmittedAt(),
          row.getTimeTakenSeconds()
        };
        for (int i = 0; i < cells.length; i++) {
          if (i > 0) csv.append(',');
          csv.append(csvCell(cells[i]));
        }
        csv.append("\r\n");
      }
    }
    return csv.toString();
  }

  public static String csvCell(Object value) {
    String s = value == null ? "" : String.valueOf(value);
    String trimmed = s.stripLeading();
    if (s.startsWith("\t")
        || s.startsWith("\r")
        || (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)) s = "'" + s;
    return "\"" + s.replace("\"", "\"\"") + "\"";
  }

  private Specification<Attempt> spec(Quiz q, ReportFilter f, Instant now) {
    if (f.getFrom() != null && f.getTo() != null && !f.getFrom().isBefore(f.getTo()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must precede to");
    String status = f.getStatus() == null ? "" : f.getStatus().trim().toUpperCase(Locale.ROOT);
    if (!status.isEmpty() && !Set.of("IN_PROGRESS", "SUBMITTED", "EXPIRED").contains(status))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown attempt status");
    return (root, query, cb) -> {
      List<Predicate> p = new ArrayList<>();
      p.add(cb.equal(root.get("quiz").get("id"), q.getId()));
      if (status.equals("EXPIRED"))
        p.add(
            cb.or(
                cb.equal(root.get("status"), Attempt.Status.EXPIRED),
                cb.and(
                    cb.equal(root.get("status"), Attempt.Status.IN_PROGRESS),
                    cb.lessThanOrEqualTo(root.get("deadline"), now))));
      if (status.equals("IN_PROGRESS"))
        p.add(
            cb.and(
                cb.equal(root.get("status"), Attempt.Status.IN_PROGRESS),
                cb.greaterThan(root.get("deadline"), now)));
      if (status.equals("SUBMITTED")) p.add(cb.equal(root.get("status"), Attempt.Status.SUBMITTED));
      if (f.getSearch() != null && !f.getSearch().isBlank()) {
        String literal =
            f.getSearch()
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        var student = root.join("student");
        p.add(
            cb.or(
                cb.like(cb.lower(student.get("userName")), "%" + literal + "%", '\\'),
                cb.like(cb.lower(student.get("email")), "%" + literal + "%", '\\')));
      }
      if (f.getFrom() != null) p.add(cb.greaterThanOrEqualTo(root.get("startedAt"), f.getFrom()));
      if (f.getTo() != null) p.add(cb.lessThan(root.get("startedAt"), f.getTo()));
      if (f.getPassed() != null) {
        p.add(cb.isNotNull(root.get("score")));
        var score = cb.prod(root.<Integer>get("score"), 100);
        int threshold = q.getPassingPercent() * q.getSnapshots().size();
        p.add(f.getPassed() ? cb.ge(score, threshold) : cb.lt(score, threshold));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
  }

  private AdminAttemptRow row(Attempt a, Quiz q, Instant now) {
    AdminAttemptRow r = new AdminAttemptRow();
    r.setId(a.getId());
    r.setStudentId(a.getStudentId());
    r.setStudentName(a.getStudent().getUserName());
    r.setStudentEmail(a.getStudent().getEmail());
    r.setStatus(
        a.getStatus() == Attempt.Status.IN_PROGRESS && !now.isBefore(a.getDeadline())
            ? "EXPIRED"
            : a.getStatus().name());
    r.setAttemptNumber(a.getAttemptNumber());
    r.setScore(a.getScore());
    r.setTotal(q.getSnapshots().size());
    r.setPassed(
        a.getScore() == null ? null : a.getScore() * 100 >= q.getPassingPercent() * r.getTotal());
    r.setStartedAt(a.getStartedAt());
    r.setDeadline(a.getDeadline());
    r.setSubmittedAt(a.getSubmittedAt());
    r.setTimeTakenSeconds(
        a.getSubmittedAt() == null
            ? null
            : Duration.between(a.getStartedAt(), a.getSubmittedAt()).getSeconds());
    return r;
  }
}
