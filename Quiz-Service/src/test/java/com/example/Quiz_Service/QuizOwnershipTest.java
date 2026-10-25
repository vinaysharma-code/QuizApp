package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.Quiz_Service.dto.request.DraftQuizRequest;
import com.example.Quiz_Service.entity.*;
import com.example.Quiz_Service.feign.QuizInterface;
import com.example.Quiz_Service.repository.*;
import com.example.Quiz_Service.service.QuizService;
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
class QuizOwnershipTest {
  @Mock QuizRepo quizRepo;
  @Mock UserRepo userRepo;
  @Mock QuizInterface quizInterface;
  @Mock Clock clock;
  @InjectMocks QuizService service;

  @BeforeEach
  void setup() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken("admin@example.test", null, List.of()));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void user() {
    User u = new User();
    u.setId("admin");
    when(userRepo.findByEmail("admin@example.test")).thenReturn(Optional.of(u));
  }

  DraftQuizRequest draft() {
    DraftQuizRequest r = new DraftQuizRequest();
    r.setTitle("Draft");
    r.setQuestionIds(List.of("q1"));
    return r;
  }

  @Test
  void anotherOwner_shouldBeDeniedBeforeReadingBank() {
    user();
    Quiz q = new Quiz();
    q.setOwnerId("someone-else");
    when(quizRepo.findById("quiz")).thenReturn(Optional.of(q));
    var ex = assertThrows(ResponseStatusException.class, () -> service.preview("quiz"));
    assertEquals(403, ex.getStatusCode().value());
    verifyNoInteractions(quizInterface);
  }

  @Test
  void publishedRules_shouldNotBeEditable() {
    user();
    Quiz q = new Quiz();
    q.setOwnerId("admin");
    q.setPublished(true);
    when(quizRepo.findLockedById("quiz")).thenReturn(Optional.of(q));
    var ex =
        assertThrows(ResponseStatusException.class, () -> service.updateDraft("quiz", draft()));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void invalidWindow_shouldFailBeforeSaving() {
    DraftQuizRequest r = draft();
    r.setOpensAt(Instant.EPOCH.plusSeconds(1));
    r.setClosesAt(Instant.EPOCH);
    assertThrows(ResponseStatusException.class, () -> service.createDraft(r));
    verifyNoInteractions(quizRepo);
  }

  @Test
  void duplicateSelection_shouldFailBeforeSaving() {
    DraftQuizRequest r = draft();
    r.setQuestionIds(List.of("q1", "q1"));
    assertThrows(ResponseStatusException.class, () -> service.createDraft(r));
    verifyNoInteractions(quizRepo);
  }
}
