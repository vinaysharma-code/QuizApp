package com.example.Quiz_Service.repository;

import com.example.Quiz_Service.entity.Attempt;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AttemptRepo
    extends JpaRepository<Attempt, String>, JpaSpecificationExecutor<Attempt> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Attempt a where a.id=:id")
  Optional<Attempt> findLockedById(@Param("id") String id);

  Page<Attempt> findByStudentIdOrderByStartedAtDesc(String studentId, Pageable pageable);

  java.util.Optional<Attempt> findByQuizIdAndStudentIdAndStartKey(
      String quizId, String studentId, String startKey);

  java.util.Optional<Attempt>
      findFirstByQuizIdAndStudentIdAndStatusAndDeadlineAfterOrderByStartedAtDesc(
          String quizId, String studentId, Attempt.Status status, java.time.Instant now);

  @Query(
      "select a.id from Attempt a where"
          + " a.status=com.example.Quiz_Service.entity.Attempt.Status.IN_PROGRESS and"
          + " a.deadline<=:now order by a.deadline")
  java.util.List<String> due(
      @Param("now") java.time.Instant now, org.springframework.data.domain.Pageable page);

  long countByQuizIdAndStudentId(String quizId, String studentId);

  @Override
  @EntityGraph(attributePaths = {"student"})
  org.springframework.data.domain.Page<Attempt> findAll(
      org.springframework.data.jpa.domain.Specification<Attempt> specification,
      org.springframework.data.domain.Pageable pageable);

  @Query("select count(distinct a.studentId) from Attempt a where a.quiz.id=:quizId")
  long countParticipants(@Param("quizId") String quizId);

  @Query("select avg(a.score) from Attempt a where a.quiz.id=:quizId and a.score is not null")
  Double averageScore(@Param("quizId") String quizId);

  @Query("select max(a.score) from Attempt a where a.quiz.id=:quizId and a.score is not null")
  Integer highestScore(@Param("quizId") String quizId);

  @Query("select count(a) from Attempt a where a.quiz.id=:quizId and a.score*100>=:threshold")
  long passedCount(@Param("quizId") String quizId, @Param("threshold") int threshold);

  @Query(
      "select a.id from Attempt a where a.studentId=:student and"
          + " a.status=com.example.Quiz_Service.entity.Attempt.Status.IN_PROGRESS and"
          + " a.deadline<=:now")
  java.util.List<String> dueForStudent(
      @Param("student") String student, @Param("now") java.time.Instant now);
}
