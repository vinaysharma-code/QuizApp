package com.example.Question_Service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.Question_Service.entity.*;
import com.example.Question_Service.repository.QuestionRepo;
import com.example.Question_Service.service.QuestionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class QuestionServiceApplicationTests {
  @org.junit.jupiter.api.BeforeEach
  void authenticate() {
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin@example.test", null, List.of()));
  }

  @org.junit.jupiter.api.AfterEach
  void clear() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Autowired QuestionRepo repo;
  @Autowired QuestionService service;

  private Question question(String title) {
    Question q = new Question();
    q.setQuestionTitle(title);
    q.setCategory("  JaVa  ");
    q.setOptionA("JVM");
    q.setOptionB("HTML");
    q.setOptionC("CSS");
    q.setOptionD("SQL");
    q.setRightAnswer("JVM");
    return q;
  }

  @Test
  void postgresRandomQueryAndScoring() {
    service.addQuestion(question("runtime-one"));
    service.addQuestion(question("runtime-two"));
    repo.flush();
    List<String> ids = service.getQuestionsForQuiz(" JAVA ", 2);
    assertEquals(2, ids.size());
    assertNotEquals(ids.get(0), ids.get(1));
    assertTrue(ids.stream().allMatch(id -> id.length() == 36));
    assertEquals(
        ids, service.getQuestionsFromID(ids).stream().map(QuestionWrapper::getId).toList());
    Response answer = new Response();
    answer.setId(ids.get(0));
    answer.setResponse("JVM");
    assertEquals(1, service.getScore(List.of(answer, answer)));
  }

  @Test
  void postgresEnforcesDuplicateCategoryTitle() {
    service.addQuestion(question("duplicate"));
    repo.flush();
    assertThrows(
        DataIntegrityViolationException.class,
        () -> {
          service.addQuestion(question("duplicate"));
          repo.flush();
        });
  }

  @Test
  void rejectsInsufficientQuestions() {
    service.addQuestion(question("one-only"));
    repo.flush();
    assertThrows(ResponseStatusException.class, () -> service.getQuestionsForQuiz("java", 50));
  }

  @Test
  void otherAdminCannotReadOrEditBank() {
    service.addQuestion(question("private"));
    repo.flush();
    String id = service.getAllQuestion().get(0).getId();
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "other@example.test", null, List.of()));
    assertTrue(service.getAllQuestion().isEmpty());
    assertThrows(ResponseStatusException.class, () -> service.selected(List.of(id)));
    assertThrows(
        ResponseStatusException.class, () -> service.updateQuestion(id, question("edited")));
  }
}
