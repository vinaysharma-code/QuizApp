package com.example.Quiz_Service.repository;

import com.example.Quiz_Service.entity.Quiz;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepo extends JpaRepository<Quiz, String> {
  Page<Quiz> findByPublishedTrueOrderByPublishedAtDesc(Pageable pageable);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select q from Quiz q where q.id=:id")
  java.util.Optional<Quiz> findLockedById(
      @org.springframework.data.repository.query.Param("id") String id);

  org.springframework.data.domain.Page<Quiz> findByOwnerIdOrderByIdDesc(
      String ownerId, org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      "select q from Quiz q where q.published=true and lower(q.title) like"
          + " lower(concat('%',:search,'%')) order by q.publishedAt desc,q.id desc")
  org.springframework.data.domain.Page<Quiz> searchPublished(
      @org.springframework.data.repository.query.Param("search") String search,
      org.springframework.data.domain.Pageable page);
}
