package com.example.Quiz_Service.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class AttemptResponse {
  private String id;
  private String quizId;
  private String title;
  private String status;
  private Instant startedAt;
  private Instant deadline;
  private Instant submittedAt;
  private Integer score;
  private Boolean passed;
  private boolean resultReleased;
  private int attemptNumber;
  private long version;
  private boolean resumed;
  private int total;
  private List<StudentQuestion> questions;
  private Map<String, String> responses;
}
