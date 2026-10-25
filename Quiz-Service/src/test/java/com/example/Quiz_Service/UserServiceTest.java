package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.Quiz_Service.dto.request.*;
import com.example.Quiz_Service.entity.User;
import com.example.Quiz_Service.enums.Role;
import com.example.Quiz_Service.repository.UserRepo;
import com.example.Quiz_Service.security.JwtService;
import com.example.Quiz_Service.service.UserService;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock UserRepo userRepo;
  @Mock JwtService jwtService;
  @Mock AuthenticationManager authenticationManager;
  @Mock PasswordEncoder passwordEncoder;
  @InjectMocks UserService userService;

  @Test
  void register_shouldHashPasswordAndDefaultToStudent() {
    // Arrange
    RegisterRequest request = new RegisterRequest();
    request.setEmail("Student@Example.test");
    request.setUserName("Student");
    request.setPassword("LongPassword123!");
    when(passwordEncoder.encode(request.getPassword())).thenReturn("bcrypt-hash");
    // Act
    var result = userService.register(request);
    // Assert
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepo).saveAndFlush(saved.capture());
    assertEquals(Role.STUDENT, saved.getValue().getRole());
    assertEquals("bcrypt-hash", saved.getValue().getPassword());
    assertEquals("student@example.test", result.getEmail());
    assertEquals("STUDENT", result.getRole());
  }

  @Test
  void register_creatorShouldReceiveOwnerRole() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("Creator@Example.test");
    request.setUserName("Creator");
    request.setPassword("LongPassword123!");
    request.setAccountType(RegisterRequest.AccountType.CREATOR);
    when(passwordEncoder.encode(request.getPassword())).thenReturn("bcrypt-hash");
    var result = userService.register(request);
    ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
    verify(userRepo).saveAndFlush(saved.capture());
    assertEquals(Role.ADMIN, saved.getValue().getRole());
    assertEquals("ADMIN", result.getRole());
  }

  @Test
  void register_nullAccountTypeShouldBeRejected() {
    RegisterRequest request = new RegisterRequest();
    request.setEmail("creator@example.test");
    request.setUserName("Creator");
    request.setPassword("LongPassword123!");
    request.setAccountType(null);
    when(passwordEncoder.encode(request.getPassword())).thenReturn("bcrypt-hash");
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> userService.register(request));
    verify(userRepo, never()).saveAndFlush(any());
  }

  @Test
  void login_shouldAuthenticateBeforeIssuingJwt() {
    LoginRequest request = new LoginRequest();
    request.setEmail("student@example.test");
    request.setPassword("LongPassword123!");
    User user = new User();
    user.setId("id");
    user.setEmail(request.getEmail());
    user.setRole(Role.STUDENT);
    user.setUserName("Student");
    when(userRepo.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
    when(jwtService.generateToken(user.getEmail(), "id", "STUDENT", 0)).thenReturn("signed-token");
    var result = userService.login(request);
    InOrder order = inOrder(authenticationManager, userRepo, jwtService);
    order.verify(authenticationManager).authenticate(any());
    order.verify(userRepo).findByEmail(user.getEmail());
    order.verify(jwtService).generateToken(user.getEmail(), "id", "STUDENT", 0);
    assertEquals("signed-token", result.getToken());
  }

  @Test
  void login_shouldNotIssueTokenWhenAuthenticationFails() {
    LoginRequest request = new LoginRequest();
    request.setEmail("student@example.test");
    request.setPassword("wrong-password");
    when(authenticationManager.authenticate(any()))
        .thenThrow(new BadCredentialsException("Bad credentials"));
    assertThrows(BadCredentialsException.class, () -> userService.login(request));
    verifyNoInteractions(jwtService, userRepo);
  }
}
