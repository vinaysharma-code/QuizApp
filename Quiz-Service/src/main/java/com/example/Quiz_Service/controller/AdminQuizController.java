package com.example.Quiz_Service.controller;

import com.example.Quiz_Service.dto.request.*;
import com.example.Quiz_Service.dto.response.*;
import com.example.Quiz_Service.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/quizzes")
public class AdminQuizController {
  private final QuizService quizzes;
  private final AdminReportService reports;

  public AdminQuizController(QuizService quizzes, AdminReportService reports) {
    this.quizzes = quizzes;
    this.reports = reports;
  }

  @PostMapping
  public ResponseEntity<DraftQuizResponse> create(@Valid @RequestBody DraftQuizRequest request) {
    return ResponseEntity.status(201).body(quizzes.createDraft(request));
  }

  @GetMapping
  public Page<QuizSummary> mine(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    QuizController.checkPage(page, size);
    return quizzes.mine(page, size);
  }

  @GetMapping("/{id}")
  public DraftQuizResponse get(@PathVariable String id) {
    return quizzes.getDraft(id);
  }

  @PutMapping("/{id}")
  public DraftQuizResponse update(
      @PathVariable String id, @Valid @RequestBody DraftQuizRequest request) {
    return quizzes.updateDraft(id, request);
  }

  @GetMapping("/{id}/preview")
  public DraftQuizResponse preview(@PathVariable String id) {
    return quizzes.preview(id);
  }

  @PostMapping("/{id}/publish")
  public QuizSummary publish(@PathVariable String id) {
    return quizzes.publish(id);
  }

  @PostMapping("/{id}/release-results")
  public QuizSummary release(@PathVariable String id) {
    return quizzes.releaseResults(id);
  }

  @GetMapping("/{id}/attempts")
  public Page<AdminAttemptRow> attempts(
      @PathVariable String id,
      @Valid @ModelAttribute ReportFilter filter,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    QuizController.checkPage(page, size);
    return reports.attempts(id, filter, page, size);
  }

  @GetMapping("/{id}/statistics")
  public QuizStatistics statistics(@PathVariable String id) {
    return reports.statistics(id);
  }

  @GetMapping("/{id}/results.csv")
  public ResponseEntity<String> csv(
      @PathVariable String id, @Valid @ModelAttribute ReportFilter filter) {
    String csv = reports.exportCsv(id, filter);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("quiz-results.csv").build().toString())
        .body(csv);
  }
}
