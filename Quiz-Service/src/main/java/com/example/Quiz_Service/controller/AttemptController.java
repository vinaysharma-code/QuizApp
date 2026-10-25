package com.example.Quiz_Service.controller;

import com.example.Quiz_Service.dto.response.AttemptResponse;
import com.example.Quiz_Service.entity.Response;
import com.example.Quiz_Service.service.AttemptService;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/attempts")
public class AttemptController {
  private final AttemptService service;

  public AttemptController(AttemptService service) {
    this.service = service;
  }

  @GetMapping
  public Page<AttemptResponse> history(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    QuizController.checkPage(page, size);
    return service.history(page, size);
  }

  @GetMapping("/{id}")
  public AttemptResponse get(@PathVariable String id) {
    return service.get(id);
  }

  public record SavedAnswers(
      @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 50)
          List<Response> answers,
      @jakarta.validation.constraints.Min(0) long version) {}

  @PutMapping("/{id}/answers")
  public ResponseEntity<AttemptResponse> save(
      @PathVariable String id, @jakarta.validation.Valid @RequestBody SavedAnswers request) {
    var result = service.saveAnswers(id, request.answers(), request.version());
    return ResponseEntity.status(result.getStatus().equals("EXPIRED") ? 410 : 200).body(result);
  }

  @PostMapping("/{id}/submit")
  public ResponseEntity<AttemptResponse> submit(
      @PathVariable String id, @RequestBody List<Response> responses) {
    var result = service.submit(id, responses);
    return ResponseEntity.status(result.getStatus().equals("EXPIRED") ? 410 : 200).body(result);
  }
}
