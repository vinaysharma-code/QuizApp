package com.example.Quiz_Service.dto.response;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class QuizStatistics {
  private String quizId;
  private String title;
  private long uniqueParticipants;
  private long totalAttempts;
  private long submittedAttempts;
  private long expiredAttempts;
  private long inProgressAttempts;
  private long passedAttempts;
  private Double averageScore;
  private Integer highestScore;
  private int totalMarks;
}
