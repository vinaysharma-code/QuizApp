package com.example.Quiz_Service.service;

import com.example.Quiz_Service.dto.request.*;
import com.example.Quiz_Service.dto.response.*;
import com.example.Quiz_Service.entity.User;
import com.example.Quiz_Service.enums.Role;
import com.example.Quiz_Service.repository.UserRepo;
import com.example.Quiz_Service.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
  @org.springframework.beans.factory.annotation.Value("${account.require-verification:false}")
  private boolean requireVerification;

  private final UserRepo userRepo;
  private final PasswordEncoder passwordEncoder;
  private final AuthenticationManager authenticationManager;
  private final JwtService jwtService;

  public UserService(
      UserRepo userRepo,
      JwtService jwtService,
      AuthenticationManager authenticationManager,
      PasswordEncoder passwordEncoder) {
    this.userRepo = userRepo;
    this.jwtService = jwtService;
    this.authenticationManager = authenticationManager;
    this.passwordEncoder = passwordEncoder;
  }

  public RegisterResponse register(RegisterRequest request) {
    String email = normalize(request.getEmail());
    if (userRepo.existsByEmail(email))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password exceeds BCrypt's 72-byte limit");
    User user = new User();
    user.setEmail(email);
    user.setUserName(request.getUserName().trim());
    user.setPassword(passwordEncoder.encode(request.getPassword()));
    if (request.getAccountType() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an account type");
    user.setRole(request.getAccountType() == RegisterRequest.AccountType.CREATOR ? Role.ADMIN : Role.STUDENT);
    user.setActive(true);
    userRepo.saveAndFlush(user);
    return new RegisterResponse(user.getUserName(), email, user.getRole().name());
  }

  public LoginResponse login(LoginRequest request) {
    String email = normalize(request.getEmail());
    if (request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72)
      throw new BadCredentialsException("Invalid credentials");
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(email, request.getPassword()));
    User user =
        userRepo
            .findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
    if (requireVerification && !user.isEmailVerified())
      throw new BadCredentialsException("Verify your email before signing in");
    return new LoginResponse(
        jwtService.generateToken(email, user.getId(), user.getRole().name(), user.getAuthVersion()),
        user.getUserName(),
        email,
        user.getRole().name());
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
