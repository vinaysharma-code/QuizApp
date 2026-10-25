package com.example.Quiz_Service.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoginThrottle {
  private final JdbcTemplate db;

  public LoginThrottle(JdbcTemplate db) {
    this.db = db;
  }

  public void check(String email) {
    String key = BrowserSessions.hash("account:" + email);
    Integer hits =
        db.queryForObject(
            "INSERT INTO auth_rate_limits(bucket,window_start,hits) VALUES(?,now(),1) ON"
                + " CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN"
                + " auth_rate_limits.window_start<now()-interval '15 minutes' THEN 1 ELSE"
                + " auth_rate_limits.hits+1 END,window_start=CASE WHEN"
                + " auth_rate_limits.window_start<now()-interval '15 minutes' THEN now() ELSE"
                + " auth_rate_limits.window_start END RETURNING hits",
            Integer.class,
            key);
    if (hits != null && hits > 10)
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts; retry in 15 minutes");
  }

  public void success(String email) {
    db.update(
        "DELETE FROM auth_rate_limits WHERE bucket=?", BrowserSessions.hash("account:" + email));
  }
}
