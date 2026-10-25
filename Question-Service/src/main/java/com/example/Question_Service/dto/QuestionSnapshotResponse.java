package com.example.Question_Service.dto;

import com.example.Question_Service.entity.Question;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class QuestionSnapshotResponse {
  private String id;
  private String questionTitle;
  private String optionA;
  private String optionB;
  private String optionC;
  private String optionD;
  private String rightAnswer;

  public static QuestionSnapshotResponse from(Question q) {
    return new QuestionSnapshotResponse(
        q.getId(),
        q.getQuestionTitle(),
        q.getOptionA(),
        q.getOptionB(),
        q.getOptionC(),
        q.getOptionD(),
        q.getRightAnswer());
  }
}
