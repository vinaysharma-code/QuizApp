package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.Quiz_Service.entity.*;
import com.example.Quiz_Service.repository.*;
import com.example.Quiz_Service.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AttemptStartRulesTest {
  @Mock AttemptRepo attemptRepo;
  @Mock UserRepo userRepo;
  @Mock QuizService quizService;
  @Mock Clock clock;
  @InjectMocks AttemptService service;
  Quiz quiz;
  final Instant now = Instant.parse("2026-10-24T00:00:00Z");

  @BeforeEach
  void setup() {
    quiz = new Quiz();
    quiz.setId("quiz");
    quiz.setSnapshots(
        List.of(new SnapshotQuestion("q1", "Runtime?", "JVM", "CSS", "HTML", "SQL", "JVM")));
    when(quizService.findPublished("quiz")).thenReturn(quiz);
    when(clock.instant()).thenReturn(now);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("student@example.test", null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void user() {
    User u = new User();
    u.setId("student");
    when(userRepo.findByEmail("student@example.test")).thenReturn(Optional.of(u));
    when(userRepo.findLockedById("student")).thenReturn(Optional.of(u));
  }

  @Test
  void futureQuiz_shouldRejectStarting() {
    user();
    quiz.setOpensAt(now.plusSeconds(1));
    var e = assertThrows(ResponseStatusException.class, () -> service.start("quiz"));
    assertEquals(409, e.getStatusCode().value());
    verifyNoInteractions(attemptRepo);
  }

  @Test
  void closedQuiz_shouldRejectStartingAtExactClose() {
    user();
    quiz.setClosesAt(now);
    var e = assertThrows(ResponseStatusException.class, () -> service.start("quiz"));
    assertEquals(410, e.getStatusCode().value());
    verifyNoInteractions(attemptRepo);
  }

  @Test
  void usedLimit_shouldRejectStarting() {
    user();
    quiz.setMaxAttempts(1);
    when(attemptRepo.countByQuizIdAndStudentId("quiz", "student")).thenReturn(1L);
    assertThrows(ResponseStatusException.class, () -> service.start("quiz"));
    verify(attemptRepo, never()).saveAndFlush(any());
  }

  @Test
  void closingTime_shouldCapDeadlineAndSequenceAttempts() {
    user();
    quiz.setClosesAt(now.plusSeconds(10));
    quiz.setDurationSeconds(600);
    when(attemptRepo.countByQuizIdAndStudentId("quiz", "student")).thenReturn(1L);
    when(attemptRepo.saveAndFlush(any()))
        .thenAnswer(
            c -> {
              Attempt a = c.getArgument(0);
              a.setId("attempt");
              return a;
            });
    var result = service.start("quiz");
    assertEquals(now.plusSeconds(10), result.getDeadline());
    assertEquals(2, result.getAttemptNumber());
    verify(userRepo).findLockedById("student");
  }

  @Test
  void activeAttemptResumesBeforeConsumingAnotherAllowance() {
    user();
    Attempt active = new Attempt();
    active.setId("existing");
    active.setQuiz(quiz);
    active.setStudentId("student");
    active.setDeadline(now.plusSeconds(60));
    when(attemptRepo.findFirstByQuizIdAndStudentIdAndStatusAndDeadlineAfterOrderByStartedAtDesc(
            "quiz", "student", Attempt.Status.IN_PROGRESS, now))
        .thenReturn(Optional.of(active));
    var result = service.start("quiz");
    assertEquals("existing", result.getId());
    assertTrue(result.isResumed());
    verify(attemptRepo, never()).countByQuizIdAndStudentId(anyString(), anyString());
    verify(attemptRepo, never()).saveAndFlush(any());
  }
}
