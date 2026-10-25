package com.example.Quiz_Service.dto.response;

import java.time.Instant;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class QuizSummary {
  private String id;
  private String title;
  private int durationSeconds;
  private int questionCount;
  private Instant publishedAt;
  private boolean published;
  private Instant opensAt;
  private Instant closesAt;
  private int maxAttempts;
  private int passingPercent;
  private String resultReleasePolicy;
  private Instant resultsReleaseAt;
  private boolean resultsReleased;
  private boolean openNow;
}
