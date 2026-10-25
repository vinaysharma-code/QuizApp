package com.example.Quiz_Service.entity;

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
}
