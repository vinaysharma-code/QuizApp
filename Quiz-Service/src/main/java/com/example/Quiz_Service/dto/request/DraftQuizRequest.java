package com.example.Quiz_Service.dto.request;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class DraftQuizRequest {
  @jakarta.validation.constraints.NotBlank
  @jakarta.validation.constraints.Size(max = 100)
  private String title;

  @jakarta.validation.constraints.Min(30)
  @jakarta.validation.constraints.Max(7200)
  private int durationSeconds = 600;

  @jakarta.validation.constraints.NotEmpty
  @jakarta.validation.constraints.Size(max = 50)
  private java.util.List<@jakarta.validation.constraints.NotBlank String> questionIds;

  private java.time.Instant opensAt;
  private java.time.Instant closesAt;

  @jakarta.validation.constraints.Min(1)
  @jakarta.validation.constraints.Max(100)
  private int maxAttempts = 3;

  @jakarta.validation.constraints.Min(0)
  @jakarta.validation.constraints.Max(100)
  private int passingPercent = 50;

  @jakarta.validation.constraints.NotNull
  private com.example.Quiz_Service.enums.ResultReleasePolicy resultReleasePolicy =
      com.example.Quiz_Service.enums.ResultReleasePolicy.IMMEDIATE;

  private java.time.Instant resultsReleaseAt;
}
