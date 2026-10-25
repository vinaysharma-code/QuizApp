package com.example.Quiz_Service.controller;

import com.example.Quiz_Service.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AccountController {
  private final AccountRecovery recovery;
  private final LoginThrottle throttle;

  public AccountController(AccountRecovery recovery, LoginThrottle throttle) {
    this.recovery = recovery;
    this.throttle = throttle;
  }

  public record Email(
      @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email) {}

  public record Link(@NotBlank @Size(max = 200) String token, @Size(max = 72) String password) {}

  private Map<String, String> request(Email input, String purpose) {
    throttle.check(purpose + ":" + input.email().trim().toLowerCase(java.util.Locale.ROOT));
    recovery.request(input.email(), purpose);
    return Map.of(
        "message",
        "If the account is eligible, an email will be sent. Check your inbox or request a new link"
            + " later.");
  }

  @PostMapping("/forgot-password")
  public Map<String, String> forgot(@Valid @RequestBody Email input) {
    return request(input, "RESET");
  }

  @PostMapping("/request-verification")
  public Map<String, String> verification(@Valid @RequestBody Email input) {
    return request(input, "VERIFY");
  }

  @PostMapping("/reset-password")
  public Map<String, String> reset(@Valid @RequestBody Link input) {
    recovery.consume(input.token(), "RESET", input.password());
    return Map.of("message", "Password changed. Sign in again.");
  }

  @PostMapping("/verify-email")
  public Map<String, String> verify(@Valid @RequestBody Link input) {
    recovery.consume(input.token(), "VERIFY", null);
    return Map.of("message", "Email verified. You can now sign in.");
  }
}
