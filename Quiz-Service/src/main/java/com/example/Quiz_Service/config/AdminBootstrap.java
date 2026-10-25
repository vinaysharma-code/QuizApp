package com.example.Quiz_Service.config;

import com.example.Quiz_Service.entity.User;
import com.example.Quiz_Service.enums.Role;
import com.example.Quiz_Service.repository.UserRepo;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
  @Bean
  ApplicationRunner bootstrapAdmin(
      UserRepo repo,
      PasswordEncoder encoder,
      @Value("${bootstrap.admin.email:}") String rawEmail,
      @Value("${bootstrap.admin.password:}") String password) {
    return args -> {
      if (rawEmail.isBlank() && password.isBlank()) return;
      String email = rawEmail.trim().toLowerCase(Locale.ROOT);
      if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")
          || email.length() > 254
          || password.length() < 12
          || password.getBytes(StandardCharsets.UTF_8).length > 72)
        throw new IllegalArgumentException(
            "Provide a valid bootstrap email and 12-to-72-byte password together");
      var existing = repo.findByEmail(email);
      if (existing.isPresent()) {
        if (existing.get().getRole() != Role.ADMIN)
          throw new IllegalStateException(
              "Bootstrap email already belongs to a non-admin; refusing promotion");
        return;
      }
      User admin = new User();
      admin.setEmail(email);
      admin.setUserName("Administrator");
      admin.setPassword(encoder.encode(password));
      admin.setRole(Role.ADMIN);
      admin.setActive(true);
      repo.saveAndFlush(admin);
    };
  }
}
