package com.example.Question_Service.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionWrapper {
  private String id;
  private String questionTitle;
  private String optionA;
  private String optionB;
  private String optionC;
  private String optionD;

  public static QuestionWrapper from(Question q) {
    return new QuestionWrapper(
        q.getId(),
        q.getQuestionTitle(),
        q.getOptionA(),
        q.getOptionB(),
        q.getOptionC(),
        q.getOptionD());
  }
}
