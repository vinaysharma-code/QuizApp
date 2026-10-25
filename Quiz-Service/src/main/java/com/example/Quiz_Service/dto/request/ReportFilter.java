package com.example.Quiz_Service.dto.request;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class ReportFilter {
  @jakarta.validation.constraints.Size(max = 30)
  private String status;

  @jakarta.validation.constraints.Size(max = 100)
  private String search;

  @org.springframework.format.annotation.DateTimeFormat(
      iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
  private java.time.Instant from;

  @org.springframework.format.annotation.DateTimeFormat(
      iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
  private java.time.Instant to;

  private Boolean passed;
}
