package com.example.Quiz_Service.dto.response;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class AdminAttemptRow {
  private String id;
  private String studentId;
  private String studentName;
  private String studentEmail;
  private String status;
  private int attemptNumber;
  private Integer score;
  private Boolean passed;
  private int total;
  private java.time.Instant startedAt;
  private java.time.Instant deadline;
  private java.time.Instant submittedAt;
  private Long timeTakenSeconds;
}
