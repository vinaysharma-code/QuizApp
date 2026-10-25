package com.example.Quiz_Service.repository;

import com.example.Quiz_Service.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<User, String> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from User u where u.id=:id")
  Optional<User> findLockedById(@org.springframework.data.repository.query.Param("id") String id);
}
