package com.example.Quiz_Service.service;

import com.example.Quiz_Service.repository.AttemptRepo;
import java.time.Clock;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class ExpiryWorker {
  private final AttemptRepo attempts;
  private final AttemptService service;
  private final Clock clock;
  private final org.springframework.jdbc.core.JdbcTemplate db;

  public ExpiryWorker(
      AttemptRepo attempts,
      AttemptService service,
      Clock clock,
      org.springframework.jdbc.core.JdbcTemplate db) {
    this.attempts = attempts;
    this.service = service;
    this.clock = clock;
    this.db = db;
  }

  @Scheduled(fixedDelay = 10000, initialDelay = 30000)
  public void expire() {
    for (String id :
        attempts.due(clock.instant(), org.springframework.data.domain.PageRequest.of(0, 100)))
      service.finalizeExpired(id);
  }

  @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
  public void cleanup() {
    db.update("DELETE FROM refresh_sessions WHERE expires_at<now()-interval '1 day'");
    db.update("DELETE FROM account_tokens WHERE expires_at<now()-interval '1 day'");
    db.update("DELETE FROM auth_rate_limits WHERE window_start<now()-interval '1 day'");
  }
}
