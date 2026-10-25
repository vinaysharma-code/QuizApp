package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.Quiz_Service.security.JwtService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.util.*;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  final byte[] bytes = new byte[64];

  JwtService service() {
    Arrays.fill(bytes, (byte) 42);
    return new JwtService(Base64.getEncoder().encodeToString(bytes), 900000, "quiz-platform");
  }

  @Test
  void jwt_shouldRejectTamperingAndWrongSigningKey() {
    JwtService service = service();
    String token = service.generateToken("student@example.test", "id", "STUDENT");
    assertEquals("student@example.test", service.extractEmail(token));
    assertThrows(
        JwtException.class,
        () -> service.extractAllClaims(token.substring(0, token.lastIndexOf('.') + 1) + "AAAA"));
    byte[] wrong = new byte[64];
    Arrays.fill(wrong, (byte) 99);
    var other = new JwtService(Base64.getEncoder().encodeToString(wrong), 900000, "quiz-platform");
    assertThrows(JwtException.class, () -> other.extractAllClaims(token));
  }

  @Test
  void jwt_shouldRejectExpiredToken() {
    JwtService service = service();
    String expired =
        Jwts.builder()
            .subject("student@example.test")
            .issuer("quiz-platform")
            .claim("type", "access")
            .claim("role", "STUDENT")
            .claim("userId", "id")
            .issuedAt(new Date(0))
            .expiration(new Date(1000))
            .signWith(Keys.hmacShaKeyFor(bytes))
            .compact();
    assertThrows(ExpiredJwtException.class, () -> service.extractAllClaims(expired));
  }

  @Test
  void jwt_shouldRejectMissingRoleAsInvalidToken() {
    JwtService service = service();
    String token =
        Jwts.builder()
            .subject("student@example.test")
            .issuer("quiz-platform")
            .claim("type", "access")
            .claim("userId", "id")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 900000))
            .signWith(Keys.hmacShaKeyFor(bytes))
            .compact();
    assertThrows(JwtException.class, () -> service.extractAllClaims(token));
  }
}
