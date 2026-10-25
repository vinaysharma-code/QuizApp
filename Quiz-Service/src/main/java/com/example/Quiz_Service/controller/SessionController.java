package com.example.Quiz_Service.controller;

import com.example.Quiz_Service.dto.request.LoginRequest;
import com.example.Quiz_Service.dto.response.LoginResponse;
import com.example.Quiz_Service.service.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
public class SessionController {
  private final UserService users;
  private final BrowserSessions sessions;
  private final boolean secure;
  private final LoginThrottle throttle;
  private final com.example.Quiz_Service.security.JwtService jwt;

  public SessionController(
      UserService users,
      BrowserSessions sessions,
      LoginThrottle throttle,
      com.example.Quiz_Service.security.JwtService jwt,
      @Value("${session.cookie-secure:true}") boolean secure) {
    this.users = users;
    this.sessions = sessions;
    this.secure = secure;
    this.throttle = throttle;
    this.jwt = jwt;
  }

  private void client(String client) {
    if (!"quiz-browser".equals(client))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing browser request header");
  }

  private void cookie(HttpServletResponse res, String raw) {
    res.addHeader(
        HttpHeaders.SET_COOKIE,
        ResponseCookie.from("quiz_refresh", raw)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Strict")
            .path("/auth")
            .maxAge(raw.isEmpty() ? 0 : 28800)
            .build()
            .toString());
    res.setHeader("Cache-Control", "no-store");
  }

  @PostMapping("/session")
  public LoginResponse login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = "X-Quiz-Client", defaultValue = "") String client,
      HttpServletResponse response) {
    client(client);
    String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
    throttle.check(email);
    var login = users.login(request);
    throttle.success(email);
    var session =
        sessions.issueAuthenticated(
            login.getEmail(),
            jwt.extractAllClaims(login.getToken()).get("authVersion", Number.class).longValue());
    cookie(response, session.refresh());
    return session.profile();
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(
      @CookieValue(value = "quiz_refresh", required = false) String raw,
      @RequestHeader(value = "X-Quiz-Client", defaultValue = "") String client,
      HttpServletResponse response) {
    client(client);
    var session = sessions.refresh(raw);
    cookie(response, session.refresh());
    return session.profile();
  }

  @PostMapping("/logout")
  public void logout(
      @CookieValue(value = "quiz_refresh", required = false) String raw,
      @RequestHeader(value = "X-Quiz-Client", defaultValue = "") String client,
      HttpServletResponse response) {
    client(client);
    sessions.logout(raw);
    cookie(response, "");
  }
}
