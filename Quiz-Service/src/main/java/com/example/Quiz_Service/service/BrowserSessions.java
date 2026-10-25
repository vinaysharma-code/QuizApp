package com.example.Quiz_Service.service;

import com.example.Quiz_Service.dto.response.LoginResponse;
import com.example.Quiz_Service.repository.UserRepo;
import com.example.Quiz_Service.security.JwtService;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BrowserSessions {
  private final JdbcTemplate db;
  private final UserRepo users;
  private final JwtService jwt;
  private final Clock clock;

  public BrowserSessions(JdbcTemplate db, UserRepo users, JwtService jwt, Clock clock) {
    this.db = db;
    this.users = users;
    this.jwt = jwt;
    this.clock = clock;
  }

  public record Session(LoginResponse profile, String refresh) {}

  public static String random() {
    byte[] b = new byte[32];
    new SecureRandom().nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  public static String hash(String raw) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional
  public Session issue(String email) {
    var u = users.findByEmail(email).orElseThrow();
    return create(
        u.getId(), UUID.randomUUID().toString(), clock.instant().plus(Duration.ofHours(8)));
  }

  @Transactional
  public Session issueAuthenticated(String email, long authenticatedVersion) {
    var found = users.findByEmail(email).orElseThrow(BrowserSessions::denied);
    var user = users.findLockedById(found.getId()).orElseThrow(BrowserSessions::denied);
    if (user.getAuthVersion() != authenticatedVersion) throw denied();
    return create(
        user.getId(), UUID.randomUUID().toString(), clock.instant().plus(Duration.ofHours(8)));
  }

  private Session create(String userId, String family, Instant expiry) {
    var user = users.findById(userId).orElseThrow();
    if (!user.isActive()) throw denied();
    String raw = random();
    db.update(
        "INSERT INTO refresh_sessions(hash,user_id,family,expires_at) VALUES(?,?,?,?)",
        hash(raw),
        userId,
        family,
        java.sql.Timestamp.from(expiry));
    var profile =
        new LoginResponse(
            jwt.generateToken(
                user.getEmail(), userId, user.getRole().name(), user.getAuthVersion()),
            user.getUserName(),
            user.getEmail(),
            user.getRole().name());
    return new Session(profile, raw);
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public Session refresh(String raw) {
    if (raw == null || raw.length() > 200) throw denied();
    String h = hash(raw);
    var rows = db.queryForList("SELECT user_id FROM refresh_sessions WHERE hash=?", h);
    if (rows.isEmpty()) throw denied();
    String userId = (String) rows.get(0).get("user_id");
    users.findLockedById(userId).orElseThrow(BrowserSessions::denied);
    var row = db.queryForMap("SELECT * FROM refresh_sessions WHERE hash=? FOR UPDATE", h);
    if (Boolean.TRUE.equals(row.get("used"))) {
      db.update("UPDATE refresh_sessions SET revoked=true WHERE family=?", row.get("family"));
      throw denied();
    }
    Instant expiry = ((java.sql.Timestamp) row.get("expires_at")).toInstant();
    if (Boolean.TRUE.equals(row.get("revoked")) || !clock.instant().isBefore(expiry))
      throw denied();
    db.update("UPDATE refresh_sessions SET used=true WHERE hash=?", h);
    return create(userId, (String) row.get("family"), expiry);
  }

  @Transactional
  public void logout(String raw) {
    if (raw == null || raw.length() > 200) return;
    var rows =
        db.queryForList("SELECT user_id,family FROM refresh_sessions WHERE hash=?", hash(raw));
    if (rows.isEmpty()) return;
    var row = rows.get(0);
    users.findLockedById((String) row.get("user_id")).orElseThrow(BrowserSessions::denied);
    db.update("UPDATE refresh_sessions SET revoked=true WHERE family=?", row.get("family"));
  }

  static ResponseStatusException denied() {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired; sign in again");
  }
}
