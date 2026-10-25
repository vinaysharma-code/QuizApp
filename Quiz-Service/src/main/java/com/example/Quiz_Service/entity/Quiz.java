package com.example.Quiz_Service.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@Entity
@Table(name = "quizzes")
public class Quiz {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(length = 36)
  private String id;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(length = 36)
  private String ownerId;

  private java.time.Instant opensAt;
  private java.time.Instant closesAt;
  private int maxAttempts = 3;
  private int passingPercent = 50;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private com.example.Quiz_Service.enums.ResultReleasePolicy resultReleasePolicy =
      com.example.Quiz_Service.enums.ResultReleasePolicy.IMMEDIATE;

  private java.time.Instant resultsReleaseAt;
  private boolean resultsReleased;
  @Version private long version;
  private int questionCount;
  private int durationSeconds = 600;
  private boolean published;
  private java.time.Instant publishedAt;

  @ElementCollection
  @CollectionTable(name = "quiz_snapshot_questions", joinColumns = @JoinColumn(name = "quiz_id"))
  @OrderColumn(name = "question_order")
  private List<SnapshotQuestion> snapshots = new ArrayList<>();

  // IDs belong to Question-Service: deliberately no cross-service foreign key.
  @ElementCollection
  @CollectionTable(name = "quiz_questions", joinColumns = @JoinColumn(name = "quiz_id"))
  @OrderColumn(name = "question_order")
  @Column(name = "question_id", nullable = false, length = 36)
  private List<String> questions = new ArrayList<>();
}
