package com.example.Quiz_Service.dto;

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
}
