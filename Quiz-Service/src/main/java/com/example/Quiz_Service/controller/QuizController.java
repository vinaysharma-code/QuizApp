package com.example.Quiz_Service.controller;

import com.example.Quiz_Service.dto.QuizDto;
import com.example.Quiz_Service.dto.response.*;
import com.example.Quiz_Service.service.*;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/quiz")
public class QuizController {
  private final QuizService quizService;
  private final AttemptService attemptService;

  public QuizController(QuizService quizService, AttemptService attemptService) {
    this.quizService = quizService;
    this.attemptService = attemptService;
  }

  @PostMapping("/create")
  public ResponseEntity<String> create(@Valid @RequestBody QuizDto dto) {
    return ResponseEntity.status(201).body(quizService.createQuiz(dto));
  }

  @GetMapping("/published")
  public org.springframework.data.domain.Page<QuizSummary> published(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "") String search) {
    checkPage(page, size);
    return quizService.published(page, size, search);
  }

  @GetMapping("/published/{id}")
  public QuizSummary publishedQuiz(@PathVariable String id) {
    return quizService.publishedQuiz(id);
  }

  @GetMapping("/getQuiz/{id}")
  public List<StudentQuestion> get(@PathVariable String id) {
    return quizService.getQuizQuestion(id);
  }

  @PostMapping("/{id}/attempts")
  public ResponseEntity<AttemptResponse> start(
      @PathVariable String id,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    var response = attemptService.start(id, key);
    return ResponseEntity.status(response.isResumed() ? 200 : 201).body(response);
  }

  static void checkPage(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "page >= 0 and size 1..100 are required");
  }
}
