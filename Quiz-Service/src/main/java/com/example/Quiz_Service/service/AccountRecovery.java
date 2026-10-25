package com.example.Quiz_Service.service;

import com.example.Quiz_Service.repository.UserRepo;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountRecovery {
  private final UserRepo users;
  private final JdbcTemplate db;
  private final JavaMailSender mail;
  private final PasswordEncoder passwords;
  private final Clock clock;
  private final String base, from;
  private final boolean enabled;
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AccountRecovery.class);

  public AccountRecovery(
      UserRepo users,
      JdbcTemplate db,
      JavaMailSender mail,
      PasswordEncoder passwords,
      Clock clock,
      @Value("${account.public-url}") String base,
      @Value("${account.mail-from}") String from,
      @Value("${account.mail-enabled:true}") boolean enabled) {
    this.users = users;
    this.db = db;
    this.mail = mail;
    this.passwords = passwords;
    this.clock = clock;
    this.base = base;
    this.from = from;
    this.enabled = enabled;
  }

  @Transactional
  public void request(String email, String purpose) {
    var optional = users.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
    if (optional.isEmpty() || !optional.get().isActive()) return;
    var user = users.findLockedById(optional.get().getId()).orElseThrow();
    if (purpose.equals("VERIFY") && user.isEmailVerified()) return;
    db.update(
        "UPDATE account_tokens SET used=true WHERE user_id=? AND purpose=?", user.getId(), purpose);
    String raw = BrowserSessions.random();
    Instant expiry =
        clock
            .instant()
            .plus(purpose.equals("RESET") ? Duration.ofMinutes(30) : Duration.ofHours(24));
    db.update(
        "INSERT INTO account_tokens(hash,user_id,purpose,expires_at) VALUES(?,?,?,?)",
        BrowserSessions.hash(raw),
        user.getId(),
        purpose,
        java.sql.Timestamp.from(expiry));
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(user.getEmail());
    message.setSubject(
        purpose.equals("RESET")
            ? "Reset your Quiz Platform password"
            : "Verify your Quiz Platform email");
    message.setText(
        "Open this link to continue: "
            + base.replaceAll("/+$", "")
            + "/demo.html#"
            + (purpose.equals("RESET") ? "reset" : "verify")
            + "="
            + raw
            + "\nThis link expires at "
            + expiry
            + " and can be used once. If you did not request it, ignore this email.");
    // No raw token is returned by the API, logged or persisted. The user can request a new link if
    // delivery fails.
    if (enabled)
      try {
        mail.send(message);
      } catch (org.springframework.mail.MailException ex) {
        LOG.warn(
            "Account email delivery failed; check SMTP configuration and request another link");
      }
  }

  @Transactional
  public void consume(String raw, String purpose, String newPassword) {
    if (raw == null || raw.length() > 200) throw invalid();
    String hash = BrowserSessions.hash(raw);
    var matches = db.queryForList("SELECT user_id FROM account_tokens WHERE hash=?", hash);
    if (matches.isEmpty()) throw invalid();
    String uid = (String) matches.get(0).get("user_id");
    var user = users.findLockedById(uid).orElseThrow(AccountRecovery::invalid);
    var token = db.queryForMap("SELECT * FROM account_tokens WHERE hash=? FOR UPDATE", hash);
    if (!user.isActive()
        || Boolean.TRUE.equals(token.get("used"))
        || !purpose.equals(token.get("purpose"))
        || !clock.instant().isBefore(((java.sql.Timestamp) token.get("expires_at")).toInstant()))
      throw invalid();
    if (purpose.equals("RESET")) {
      if (newPassword == null
          || newPassword.length() < 8
          || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Password must have at least 8 characters and at most 72 UTF-8 bytes");
      user.setPassword(passwords.encode(newPassword));
      user.setAuthVersion(user.getAuthVersion() + 1);
      db.update("UPDATE refresh_sessions SET revoked=true WHERE user_id=?", uid);
    } else user.setEmailVerified(true);
    db.update("UPDATE account_tokens SET used=true WHERE user_id=? AND purpose=?", uid, purpose);
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Invalid or expired link; request another email");
  }
}
