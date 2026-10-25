package com.example.Quiz_Service.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class QuizDto {
  @NotBlank private String categoryName;

  @NotNull
  @Min(1)
  @Max(50)
  private Integer noQ;

  @NotBlank
  @Size(max = 100)
  private String title;

  @Min(30)
  @Max(7200)
  private int durationSeconds = 600;
}
