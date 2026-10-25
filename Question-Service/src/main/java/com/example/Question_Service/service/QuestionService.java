package com.example.Question_Service.service;

import com.example.Question_Service.entity.Question;
import com.example.Question_Service.entity.QuestionWrapper;
import com.example.Question_Service.entity.Response;
import com.example.Question_Service.repository.QuestionRepo;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuestionService {

  private final QuestionRepo questionRepo;

  public QuestionService(QuestionRepo questionRepo) {
    this.questionRepo = questionRepo;
  }

  public List<Question> getAllQuestion() {
    return questionRepo.findByOwnerEmail(owner());
  }

  public List<Question> getQuestionByCategory(String category) {
    return questionRepo.findByOwnerEmailAndCategory(owner(), normalize(category));
  }

  public String addQuestion(Question q) {
    q.setId(null);
    q.setOwnerEmail(owner());
    q.setCategory(q.getCategory().trim().toLowerCase(Locale.ROOT));
    List<String> options = List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD());
    if (!options.contains(q.getRightAnswer())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "rightAnswer must match one of the four options");
    }
    questionRepo.save(q);
    return "Question added successfully";
  }

  public List<String> getQuestionsForQuiz(String category, Integer noQ) {
    if (noQ == null || noQ <= 0 || noQ > 50) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "numQuestion must be between 1 and 50");
    }
    List<Question> questions = questionRepo.ownedRandom(owner(), normalize(category), noQ);
    if (questions.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "No questions for category: " + category);
    }

    if (questions.size() < noQ) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Not enough questions in this category");
    }
    return questions.stream().map(Question::getId).distinct().toList();
  }

  public List<QuestionWrapper> getQuestionsFromID(List<String> questionIds) {
    if (questionIds == null || questionIds.isEmpty() || questionIds.size() > 100) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "1 to 100 question ids are required");
    }
    Map<String, Question> byId =
        owned(questionIds).stream().collect(Collectors.toMap(Question::getId, q -> q));
    return questionIds.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(QuestionWrapper::from)
        .toList();
  }

  public Integer getScore(List<Response> responses) {
    if (responses == null || responses.isEmpty() || responses.size() > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "1 to 100 responses are required");
    }
    Map<String, String> answers = new HashMap<>();
    for (Response r : responses) {
      if (r != null && r.getId() != null && r.getResponse() != null) {
        answers.putIfAbsent(r.getId(), r.getResponse().trim()); // first answer per question only
      }
    }
    Map<String, Question> byId =
        owned(answers.keySet()).stream().collect(Collectors.toMap(Question::getId, q -> q));
    int right = 0;
    for (Map.Entry<String, String> e : answers.entrySet()) {
      Question q = byId.get(e.getKey());
      String correct = (q == null) ? null : q.getRightAnswer();
      if (correct != null && e.getValue().equals(correct.trim())) {
        right++;
      }
    }
    return right;
  }

  public List<com.example.Question_Service.dto.QuestionSnapshotResponse> snapshot(
      String category, int count) {
    if (count < 1 || count > 50)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Question count must be 1..50");
    var questions = questionRepo.ownedRandom(owner(), normalize(category), count);
    if (questions.size() != count)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Not enough questions in category");
    return questions.stream()
        .map(com.example.Question_Service.dto.QuestionSnapshotResponse::from)
        .toList();
  }

  public String updateQuestion(String id, Question q) {
    if (questionRepo.findByIdAndOwnerEmail(id, owner()).isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found");
    q.setId(id);
    q.setOwnerEmail(owner());
    q.setCategory(normalize(q.getCategory()));
    if (!List.of(q.getOptionA(), q.getOptionB(), q.getOptionC(), q.getOptionD())
        .contains(q.getRightAnswer()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rightAnswer must match an option");
    questionRepo.saveAndFlush(q);
    return "Question updated; published quiz snapshots are unchanged";
  }

  private String owner() {
    var auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (auth == null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    return auth.getName();
  }

  private List<Question> owned(java.util.Collection<String> ids) {
    var rows = questionRepo.findByOwnerEmailAndIdIn(owner(), ids);
    if (rows.size() != new java.util.HashSet<>(ids).size())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found in your bank");
    return rows;
  }

  private String normalize(String c) {
    return c == null ? "" : c.trim().toLowerCase(Locale.ROOT);
  }

  public java.util.List<com.example.Question_Service.dto.QuestionSnapshotResponse> selected(
      java.util.List<String> ids) {
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 50
        || ids.stream().anyMatch(java.util.Objects::isNull)
        || new java.util.HashSet<>(ids).size() != ids.size())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Select 1..50 distinct question IDs");
    var rows =
        owned(ids).stream().collect(java.util.stream.Collectors.toMap(Question::getId, q -> q));
    if (rows.size() != ids.size())
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A selected question no longer exists");
    return ids.stream()
        .map(rows::get)
        .map(com.example.Question_Service.dto.QuestionSnapshotResponse::from)
        .toList();
  }
}
