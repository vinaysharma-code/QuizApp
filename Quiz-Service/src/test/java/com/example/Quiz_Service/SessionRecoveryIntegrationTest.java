package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.Quiz_Service.dto.request.*;
import com.example.Quiz_Service.repository.UserRepo;
import com.example.Quiz_Service.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "eureka.client.enabled=false",
      "account.require-verification=true",
      "account.mail-enabled=true"
    })
@ActiveProfiles("test")
@Transactional
class SessionRecoveryIntegrationTest {
  @Autowired UserService service;
  @Autowired UserRepo users;
  @Autowired BrowserSessions sessions;
  @Autowired AccountRecovery recovery;
  @Autowired JdbcTemplate db;
  @Autowired LoginThrottle throttle;
  @MockitoBean JavaMailSender mail;
  String email;

  @BeforeEach
  void create() {
    email = "test-" + java.util.UUID.randomUUID() + "@example.test";
    RegisterRequest r = new RegisterRequest();
    r.setEmail(email);
    r.setUserName("Student");
    r.setPassword("Password123!");
    service.register(r);
  }

  LoginRequest login(String password) {
    LoginRequest r = new LoginRequest();
    r.setEmail(email);
    r.setPassword(password);
    return r;
  }

  String link(String purpose) {
    recovery.request(email, purpose);
    var c = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail, atLeastOnce()).send(c.capture());
    String text = c.getValue().getText();
    return text.substring(text.indexOf("=") + 1, text.indexOf('\n'));
  }

  @Test
  void verificationIsRequiredAndLinkIsSingleUse() {
    assertThrows(
        org.springframework.security.authentication.BadCredentialsException.class,
        () -> service.login(login("Password123!")));
    String raw = link("VERIFY");
    recovery.consume(raw, "VERIFY", null);
    assertNotNull(service.login(login("Password123!")).getToken());
    assertThrows(ResponseStatusException.class, () -> recovery.consume(raw, "VERIFY", null));
  }

  @Test
  void refreshRotatesAndReplayRevokesTheFamily() {
    var first = sessions.issue(email);
    var next = sessions.refresh(first.refresh());
    assertNotEquals(first.refresh(), next.refresh());
    assertEquals(
        0,
        db.queryForObject(
            "select count(*) from refresh_sessions where hash=?", Integer.class, first.refresh()));
    assertThrows(ResponseStatusException.class, () -> sessions.refresh(first.refresh()));
    assertThrows(ResponseStatusException.class, () -> sessions.refresh(next.refresh()));
  }

  @Test
  void resetRevokesSessionsAndOldPassword() {
    var session = sessions.issue(email);
    String raw = link("RESET");
    recovery.consume(raw, "RESET", "NewPassword123!");
    assertThrows(ResponseStatusException.class, () -> sessions.refresh(session.refresh()));
    assertTrue(users.findByEmail(email).orElseThrow().getAuthVersion() > 0);
    assertThrows(
        org.springframework.security.authentication.BadCredentialsException.class,
        () -> service.login(login("Password123!")));
    assertThrows(
        ResponseStatusException.class, () -> recovery.consume(raw, "RESET", "OtherPassword123!"));
  }

  @Test
  void wrongPurposeAndExpiredLinksAreRejected() {
    String raw = link("VERIFY");
    assertThrows(
        ResponseStatusException.class, () -> recovery.consume(raw, "RESET", "NewPassword123!"));
    db.update(
        "update account_tokens set expires_at=now()-interval '1 minute' where hash=?",
        BrowserSessions.hash(raw));
    assertThrows(ResponseStatusException.class, () -> recovery.consume(raw, "VERIFY", null));
  }

  @Test
  void accountThrottleIsSharedAndResetsAfterSuccess() {
    for (int i = 0; i < 10; i++) throttle.check(email);
    assertEquals(
        429,
        assertThrows(ResponseStatusException.class, () -> throttle.check(email))
            .getStatusCode()
            .value());
    throttle.success(email);
    assertDoesNotThrow(() -> throttle.check(email));
  }

  @Test
  void logoutRevokesRefresh() {
    var session = sessions.issue(email);
    sessions.logout(session.refresh());
    assertThrows(ResponseStatusException.class, () -> sessions.refresh(session.refresh()));
  }
}
