package com.example.Quiz_Service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class SnapshotQuestion {
  @Column(name = "question_id", nullable = false, length = 36)
  private String id;

  @Column(name = "question_title", nullable = false, length = 1000)
  private String questionTitle;

  @Column(name = "option_a", nullable = false, length = 1000)
  private String optionA;

  @Column(name = "option_b", nullable = false, length = 1000)
  private String optionB;

  @Column(name = "option_c", nullable = false, length = 1000)
  private String optionC;

  @Column(name = "option_d", nullable = false, length = 1000)
  private String optionD;

  @JsonIgnore
  @Column(name = "right_answer", nullable = false, length = 1000)
  private String rightAnswer;
}
