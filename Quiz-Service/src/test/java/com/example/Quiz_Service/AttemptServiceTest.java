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
class AttemptServiceTest {
  @Mock AttemptRepo attemptRepo;
  @Mock UserRepo userRepo;
  @Mock QuizService quizService;
  @Mock Clock clock;
  @InjectMocks AttemptService service;
  Attempt attempt;
  final Instant now = Instant.parse("2026-10-24T00:00:00Z");

  @BeforeEach
  void setUp() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("student@example.test", null, List.of()));
    Quiz quiz = new Quiz();
    quiz.setId("quiz");
    quiz.setTitle("Java");
    quiz.setSnapshots(
        List.of(new SnapshotQuestion("q1", "Runtime?", "JVM", "CSS", "HTML", "SQL", "JVM")));
    attempt = new Attempt();
    attempt.setId("attempt");
    attempt.setQuiz(quiz);
    attempt.setStudentId("student");
    attempt.setStartedAt(now.minusSeconds(60));
    attempt.setDeadline(now.plusSeconds(60));
    User user = new User();
    user.setId("student");
    when(userRepo.findByEmail("student@example.test")).thenReturn(Optional.of(user));
    when(attemptRepo.findLockedById("attempt")).thenReturn(Optional.of(attempt));
  }

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
  }

  Response answer(String id, String value) {
    Response r = new Response();
    r.setId(id);
    r.setResponse(value);
    return r;
  }

  @Test
  void submit_shouldScoreOnlyOnceAndReturnRecordedResultOnReplay() {
    when(clock.instant()).thenReturn(now);
    assertEquals(1, service.submit("attempt", List.of(answer("q1", "JVM"))).getScore());
    assertEquals(1, service.submit("attempt", List.of(answer("q1", "CSS"))).getScore());
    assertEquals(Attempt.Status.SUBMITTED, attempt.getStatus());
    assertEquals("JVM", attempt.getResponses().get("q1"));
    verifyNoInteractions(quizService);
  }

  @Test
  void submit_shouldRejectForeignQuestionWithoutChangingAttempt() {
    when(clock.instant()).thenReturn(now);
    assertThrows(
        ResponseStatusException.class,
        () -> service.submit("attempt", List.of(answer("foreign", "JVM"))));
    assertEquals(Attempt.Status.IN_PROGRESS, attempt.getStatus());
    assertTrue(attempt.getResponses().isEmpty());
  }

  @Test
  void submit_shouldRejectAnotherStudentsAttempt() {
    attempt.setStudentId("someone-else");
    var error =
        assertThrows(
            ResponseStatusException.class,
            () -> service.submit("attempt", List.of(answer("q1", "JVM"))));
    assertEquals(403, error.getStatusCode().value());
  }

  @Test
  void submit_shouldExpireAtTheExactDeadline() {
    when(clock.instant()).thenReturn(attempt.getDeadline());
    var result = service.submit("attempt", List.of(answer("q1", "JVM")));
    assertEquals("EXPIRED", result.getStatus());
    assertNull(result.getScore());
    assertEquals(Attempt.Status.EXPIRED, attempt.getStatus());
  }

  @Test
  void submit_shouldUseDatabaseCompatibleTimestampPrecision() {
    when(clock.instant()).thenReturn(now.plusNanos(123456789));
    var result = service.submit("attempt", List.of(answer("q1", "JVM")));
    assertEquals(0, result.getSubmittedAt().getNano() % 1000);
  }

  @Test
  void submittedManualResult_shouldRemainHiddenOnSubmitGetAndHistoryUntilRelease() {
    when(clock.instant()).thenReturn(now);
    attempt
        .getQuiz()
        .setResultReleasePolicy(com.example.Quiz_Service.enums.ResultReleasePolicy.MANUAL);
    var submitted = service.submit("attempt", List.of(answer("q1", "JVM")));
    assertNull(submitted.getScore());
    assertNull(submitted.getPassed());
    assertFalse(submitted.isResultReleased());
    assertEquals(1, attempt.getScore());
    assertNull(service.get("attempt").getScore());
    when(attemptRepo.findByStudentIdOrderByStartedAtDesc(eq("student"), any()))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(attempt)));
    assertNull(service.history(0, 20).getContent().get(0).getScore());
    attempt.getQuiz().setResultsReleased(true);
    assertEquals(1, service.get("attempt").getScore());
    assertTrue(service.get("attempt").getPassed());
  }

  @Test
  void afterCloseResult_shouldBeHiddenUntilClosingBoundary() {
    when(clock.instant()).thenReturn(now);
    attempt
        .getQuiz()
        .setResultReleasePolicy(com.example.Quiz_Service.enums.ResultReleasePolicy.AFTER_CLOSE);
    attempt.getQuiz().setClosesAt(now.plusSeconds(10));
    assertNull(service.submit("attempt", List.of(answer("q1", "JVM"))).getScore());
    when(clock.instant()).thenReturn(now.plusSeconds(10));
    assertEquals(1, service.get("attempt").getScore());
  }

  @Test
  void atTimeResult_shouldBeReleasedAtScheduledBoundary() {
    when(clock.instant()).thenReturn(now);
    attempt
        .getQuiz()
        .setResultReleasePolicy(com.example.Quiz_Service.enums.ResultReleasePolicy.AT_TIME);
    attempt.getQuiz().setResultsReleaseAt(now.plusSeconds(10));
    assertNull(service.submit("attempt", List.of(answer("q1", "JVM"))).getScore());
    when(clock.instant()).thenReturn(now.plusSeconds(10));
    assertEquals(1, service.get("attempt").getScore());
  }

  @Test
  void autosavePersistsWithoutRevealingScore() {
    when(clock.instant()).thenReturn(now);
    var result = service.saveAnswers("attempt", List.of(answer("q1", "JVM")), 0);
    assertEquals("JVM", result.getResponses().get("q1"));
    assertNull(result.getScore());
    assertTrue(attempt.isAutosaved());
    assertEquals("IN_PROGRESS", result.getStatus());
  }

  @Test
  void staleAutosaveCannotOverwriteNewerAnswers() {
    when(clock.instant()).thenReturn(now);
    attempt.setVersion(2);
    attempt.setResponses(new HashMap<>(Map.of("q1", "JVM")));
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () -> service.saveAnswers("attempt", List.of(answer("q1", "CSS")), 1))
            .getStatusCode()
            .value());
    assertEquals("JVM", attempt.getResponses().get("q1"));
  }

  @Test
  void deadlineScoresOnlyPreviouslySavedAnswers() {
    when(clock.instant()).thenReturn(attempt.getDeadline());
    attempt.setAutosaved(true);
    attempt.setResponses(new HashMap<>(Map.of("q1", "JVM")));
    var result = service.submit("attempt", List.of(answer("q1", "CSS")));
    assertEquals("SUBMITTED", result.getStatus());
    assertEquals(1, result.getScore());
    assertEquals(attempt.getDeadline(), result.getSubmittedAt());
    assertEquals("JVM", attempt.getResponses().get("q1"));
  }
}
