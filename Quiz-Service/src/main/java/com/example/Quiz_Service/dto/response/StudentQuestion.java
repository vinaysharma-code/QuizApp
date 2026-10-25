package com.example.Quiz_Service.dto.response;

import com.example.Quiz_Service.entity.SnapshotQuestion;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class StudentQuestion {
  private String id;
  private String questionTitle;
  private String optionA;
  private String optionB;
  private String optionC;
  private String optionD;

  public static StudentQuestion from(SnapshotQuestion q) {
    return new StudentQuestion(
        q.getId(),
        q.getQuestionTitle(),
        q.getOptionA(),
        q.getOptionB(),
        q.getOptionC(),
        q.getOptionD());
  }
}
