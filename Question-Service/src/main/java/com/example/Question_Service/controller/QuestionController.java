package com.example.Question_Service.controller;

import com.example.Question_Service.entity.Question;
import com.example.Question_Service.entity.QuestionWrapper;
import com.example.Question_Service.entity.Response;
import com.example.Question_Service.service.QuestionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/question")
public class QuestionController {

  private final QuestionService questionService;

  public QuestionController(QuestionService questionService) {
    this.questionService = questionService;
  }

  @GetMapping("/getAllQuestion")
  public ResponseEntity<List<Question>> getAllQuestion() {
    return ResponseEntity.ok(questionService.getAllQuestion());
  }

  @GetMapping("/getByCategory/{category}")
  public ResponseEntity<List<Question>> getQuestionByCategory(@PathVariable String category) {
    return ResponseEntity.ok(questionService.getQuestionByCategory(category));
  }

  @PostMapping("/addQuestions")
  public ResponseEntity<String> addQuestions(@Valid @RequestBody Question question) {
    return new ResponseEntity<>(questionService.addQuestion(question), HttpStatus.CREATED);
  }

  @GetMapping("/generate")
  public ResponseEntity<List<String>> getQuestionsForQuiz(
      @RequestParam("category") String category, @RequestParam("numQuestion") Integer numQuestion) {

    return ResponseEntity.ok(questionService.getQuestionsForQuiz(category, numQuestion));
  }

  @PostMapping("/getQuestions")
  public ResponseEntity<List<QuestionWrapper>> getQuestionFromId(
      @RequestBody List<String> questionIds) {
    return ResponseEntity.ok(questionService.getQuestionsFromID(questionIds));
  }

  @PostMapping("/getScore")
  public ResponseEntity<Integer> getScore(@RequestBody List<Response> responses) {
    return ResponseEntity.ok(questionService.getScore(responses));
  }

  @GetMapping("/snapshot")
  public List<com.example.Question_Service.dto.QuestionSnapshotResponse> snapshot(
      @RequestParam String category, @RequestParam int numQuestion) {
    return questionService.snapshot(category, numQuestion);
  }

  @PutMapping("/{id}")
  public String update(@PathVariable String id, @Valid @RequestBody Question question) {
    return questionService.updateQuestion(id, question);
  }

  @PostMapping("/snapshot/selected")
  public java.util.List<com.example.Question_Service.dto.QuestionSnapshotResponse> selected(
      @RequestBody java.util.List<String> ids) {
    return questionService.selected(ids);
  }
}
