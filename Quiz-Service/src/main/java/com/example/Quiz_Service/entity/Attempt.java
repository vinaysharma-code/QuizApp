package com.example.Quiz_Service.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.Data;

@Data
@Entity
@Table(
    name = "quiz_attempts",
    indexes = @Index(name = "idx_attempt_student", columnList = "student_id"))
public class Attempt {
  public enum Status {
    IN_PROGRESS,
    SUBMITTED,
    EXPIRED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(length = 36)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "quiz_id", nullable = false)
  private Quiz quiz;

  @Column(name = "student_id", nullable = false, length = 36)
  private String studentId;

  @Column(nullable = false)
  private Instant startedAt;

  @Column(nullable = false)
  private Instant deadline;

  private Instant submittedAt;
  private Integer score;
  private int attemptNumber = 1;

  @Column(length = 100)
  private String startKey;

  private boolean autosaved;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "student_id", insertable = false, updatable = false)
  private User student;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status = Status.IN_PROGRESS;

  @Version private long version;

  @ElementCollection
  @CollectionTable(name = "attempt_responses", joinColumns = @JoinColumn(name = "attempt_id"))
  @MapKeyColumn(name = "question_id", length = 36)
  @Column(name = "answer", nullable = false, length = 1000)
  private Map<String, String> responses = new HashMap<>();
}
