package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.Quiz_Service.entity.Quiz;
import com.example.Quiz_Service.repository.QuizRepo;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(properties = "eureka.client.enabled=false")
@Transactional
class QuizServiceApplicationTests {
  @Autowired QuizRepo repo;
  @Autowired EntityManager em;

  @Test
  void postgresPersistsOrderedQuestionIdsAndCascadesCollectionDelete() {
    Quiz quiz = new Quiz();
    quiz.setTitle("Java quiz");
    List<String> ids =
        List.of("cf285b9b-dbd8-42ad-b0a9-ed6d640d8802", "0b346cd0-e36b-4467-a555-25fafb077074");
    quiz.setQuestions(ids);
    String quizId = repo.saveAndFlush(quiz).getId();
    em.clear();
    assertEquals(36, quizId.length());
    assertEquals(ids, repo.findById(quizId).orElseThrow().getQuestions());
    repo.deleteById(quizId);
    repo.flush();
    em.clear();
    Number count =
        (Number)
            em.createNativeQuery("select count(*) from quiz_questions where quiz_id = :id")
                .setParameter("id", quizId)
                .getSingleResult();
    assertEquals(0L, count.longValue());
  }
}
