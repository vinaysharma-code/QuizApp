package com.example.Question_Service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.Question_Service.security.JwtService;
import io.jsonwebtoken.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;

class RsaBoundaryTest {
  KeyPair pair;
  JwtService verifier;

  @BeforeEach
  void setup() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    pair = generator.generateKeyPair();
    verifier =
        new JwtService(
            "",
            900000,
            "quiz",
            Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
            "",
            new MockEnvironment());
  }

  String token(boolean audience) {
    var b =
        Jwts.builder()
            .subject("admin@example.test")
            .issuer("quiz")
            .claim("type", "access")
            .claim("userId", "id")
            .claim("role", "ADMIN")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 60000));
    if (audience) b.audience().add("quiz-platform").and();
    return b.signWith(pair.getPrivate(), Jwts.SIG.RS256).compact();
  }

  @Test
  void validatesIssuerSignedToken() {
    assertEquals("ADMIN", verifier.extractAllClaims(token(true)).get("role"));
  }

  @Test
  void publicKeyServiceCannotMintTokens() {
    assertThrows(
        IllegalStateException.class,
        () -> verifier.generateToken("admin@example.test", "id", "ADMIN"));
  }

  @Test
  void rejectsMissingAudience() {
    assertThrows(JwtException.class, () -> verifier.extractAllClaims(token(false)));
  }
}
