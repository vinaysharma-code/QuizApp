package com.example.Question_Service.repository;

import com.example.Question_Service.entity.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepo extends JpaRepository<Question, String> {
  List<Question> findByCategory(String category);

  @Query(
      value = "SELECT * FROM questions WHERE category = :category ORDER BY random() LIMIT :count",
      nativeQuery = true)
  List<Question> findRandomQuestionsByCategory(
      @Param("category") String category, @Param("count") int noQ);

  List<Question> findByOwnerEmail(String owner);

  List<Question> findByOwnerEmailAndCategory(String owner, String category);

  List<Question> findByOwnerEmailAndIdIn(String owner, java.util.Collection<String> ids);

  java.util.Optional<Question> findByIdAndOwnerEmail(String id, String owner);

  @Query(
      value =
          "SELECT * FROM questions WHERE owner_email=:owner AND category=:category ORDER BY"
              + " random() LIMIT :count",
      nativeQuery = true)
  List<Question> ownedRandom(
      @Param("owner") String owner, @Param("category") String category, @Param("count") int count);
}
