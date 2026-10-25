package com.example.Question_Service.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Entity
@Table(
    name = "questions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uniq_owner_category_title",
            columnNames = {"owner_email", "category", "question_title"}),
    indexes = @Index(name = "idx_question_category", columnList = "category"))
public class Question {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(length = 36)
  private String id;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(length = 254)
  private String ownerEmail;

  @NotBlank
  @Size(max = 1000)
  @Column(name = "question_title", nullable = false, length = 1000)
  private String questionTitle;

  @NotBlank
  @Size(max = 1000)
  @Column(name = "option_a", nullable = false, length = 1000)
  private String optionA;

  @NotBlank
  @Size(max = 1000)
  @Column(name = "option_b", nullable = false, length = 1000)
  private String optionB;

  @NotBlank
  @Size(max = 1000)
  @Column(name = "option_c", nullable = false, length = 1000)
  private String optionC;

  @NotBlank
  @Size(max = 1000)
  @Column(name = "option_d", nullable = false, length = 1000)
  private String optionD;

  @NotBlank
  @Size(max = 1000)
  @Column(nullable = false, length = 1000)
  private String rightAnswer;

  @Size(max = 100)
  @Column(length = 100)
  private String difficultyLevel;

  @NotBlank
  @Size(max = 100)
  @Column(nullable = false, length = 100)
  private String category;
}
