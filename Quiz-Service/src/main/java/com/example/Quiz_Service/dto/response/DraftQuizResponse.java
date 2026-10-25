package com.example.Quiz_Service.dto.response;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DraftQuizResponse {
  private QuizSummary quiz;
  private java.util.List<String> questionIds;
  private java.util.List<StudentQuestion> previewQuestions;
}
