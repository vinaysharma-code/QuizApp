package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.Quiz_Service.dto.*;
import com.example.Quiz_Service.entity.Quiz;
import com.example.Quiz_Service.feign.QuizInterface;
import com.example.Quiz_Service.repository.QuizRepo;
import com.example.Quiz_Service.service.QuizService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {
  @Mock QuizRepo quizRepo;
  @Mock QuizInterface quizInterface;
  @Mock Clock clock;
  @Mock com.example.Quiz_Service.repository.UserRepo userRepo;
  @InjectMocks QuizService service;

  QuizDto dto() {
    var d = new QuizDto();
    d.setTitle("Java");
    d.setCategoryName("java");
    d.setNoQ(1);
    return d;
  }

  @Test
  void createQuiz_shouldCopyCompleteSnapshotBeforeSaving() {
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin@example.test", null, java.util.List.of()));
    var admin = new com.example.Quiz_Service.entity.User();
    admin.setId("admin");
    when(userRepo.findByEmail("admin@example.test")).thenReturn(Optional.of(admin));
    // Arrange
    when(quizInterface.snapshot("java", 1))
        .thenReturn(
            List.of(
                new QuestionSnapshotResponse(
                    "q1", "Runtime?", "JVM", "CSS", "HTML", "SQL", "JVM")));
    when(clock.instant()).thenReturn(Instant.EPOCH);
    when(quizRepo.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              Quiz q = call.getArgument(0);
              q.setId("quiz");
              return q;
            });
    // Act
    String id = service.createQuiz(dto());
    // Assert
    ArgumentCaptor<Quiz> saved = ArgumentCaptor.forClass(Quiz.class);
    verify(quizRepo).saveAndFlush(saved.capture());
    assertEquals("quiz", id);
    assertTrue(saved.getValue().isPublished());
    assertEquals("JVM", saved.getValue().getSnapshots().get(0).getRightAnswer());
  }

  @Test
  void createQuiz_shouldNotPersistAnIncompleteUpstreamSnapshot() {
    when(quizInterface.snapshot("java", 1)).thenReturn(List.of());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.createQuiz(dto()));
    verifyNoInteractions(quizRepo);
  }

  @Test
  void publishedLink_shouldReturnOnlySafeMetadata() {
    Quiz quiz = new Quiz();
    quiz.setId("shared"); quiz.setTitle("Java quiz"); quiz.setPublished(true);
    quiz.setQuestionCount(2);
    when(quizRepo.findById("shared")).thenReturn(Optional.of(quiz));
    var result = service.publishedQuiz("shared");
    assertEquals("Java quiz", result.getTitle());
    assertEquals(2, result.getQuestionCount());
    assertTrue(result.isOpenNow());
    verifyNoInteractions(quizInterface);
  }

  @Test
  void publishedLink_shouldNotExposeDrafts() {
    when(quizRepo.findById("draft")).thenReturn(Optional.of(new Quiz()));
    var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> service.publishedQuiz("draft"));
    assertEquals(404, error.getStatusCode().value());
  }

  @org.junit.jupiter.api.AfterEach
  void cleanContext() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }
}
