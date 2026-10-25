package com.example.Quiz_Service.controller;

import com.example.Quiz_Service.dto.request.*;
import com.example.Quiz_Service.dto.response.*;
import com.example.Quiz_Service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private final UserService userService;
  private final com.example.Quiz_Service.service.AccountRecovery recovery;
  private final com.example.Quiz_Service.service.LoginThrottle throttle;

  public AuthController(
      UserService userService,
      com.example.Quiz_Service.service.LoginThrottle throttle,
      com.example.Quiz_Service.service.AccountRecovery recovery) {
    this.userService = userService;
    this.throttle = throttle;
    this.recovery = recovery;
  }

  @org.springframework.beans.factory.annotation.Value("${account.require-verification:false}")
  private boolean requireVerification;

  @org.springframework.beans.factory.annotation.Value("${account.mail-enabled:false}")
  private boolean mailEnabled;

  @GetMapping("/options")
  public java.util.Map<String, Boolean> options() {
    return java.util.Map.of("emailVerificationRequired", requireVerification,
        "emailRecoveryEnabled", mailEnabled);
  }

  @PostMapping("/register")
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    var result = userService.register(request);
    if (requireVerification) recovery.request(result.getEmail(), "VERIFY");
    return ResponseEntity.status(201).body(result);
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    String email = request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
    throttle.check(email);
    var result = userService.login(request);
    throttle.success(email);
    return result;
  }
}
