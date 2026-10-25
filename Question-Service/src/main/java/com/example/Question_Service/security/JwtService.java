package com.example.Question_Service.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtService {
  private SecretKey key;
  private java.security.PublicKey publicKey;
  private java.security.PrivateKey privateKey;
  private final long expiration;
  private final String issuer;

  @org.springframework.beans.factory.annotation.Autowired
  public JwtService(
      @Value("${jwt.secret:}") String secret,
      @Value("${jwt.expiration}") long expiration,
      @Value("${jwt.issuer}") String issuer,
      @Value("${jwt.public-key:}") String publicText,
      @Value("${jwt.private-key:}") String privateText,
      org.springframework.core.env.Environment environment) {
    this.expiration = expiration;
    this.issuer = issuer;
    if (expiration < 1000 || expiration > 3600000)
      throw new IllegalArgumentException("Invalid JWT lifetime");
    if (publicText.isBlank()) {
      if (!java.util.Arrays.asList(environment.getActiveProfiles()).contains("test"))
        throw new IllegalArgumentException(
            "JWT_PUBLIC_KEY is required; run java scripts/SetupEnv.java");
      byte[] bytes = Base64.getDecoder().decode(secret);
      if (bytes.length < 64) throw new IllegalArgumentException("Test key too short");
      key = Keys.hmacShaKeyFor(bytes);
    } else
      try {
        var factory = java.security.KeyFactory.getInstance("RSA");
        publicKey =
            factory.generatePublic(
                new java.security.spec.X509EncodedKeySpec(Base64.getDecoder().decode(publicText)));
        if (((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength() < 2048)
          throw new IllegalArgumentException("RSA key must be at least 2048 bits");
        if (!privateText.isBlank())
          privateKey =
              factory.generatePrivate(
                  new java.security.spec.PKCS8EncodedKeySpec(
                      Base64.getDecoder().decode(privateText)));

      } catch (java.security.GeneralSecurityException ex) {
        throw new IllegalArgumentException("Invalid RSA configuration", ex);
      }
  }

  public JwtService(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration}") long expiration,
      @Value("${jwt.issuer}") String issuer) {
    byte[] decoded = Base64.getDecoder().decode(secret);
    if (decoded.length < 64 || expiration < 1000 || expiration > 3600000)
      throw new IllegalArgumentException(
          "Use a Base64 key of at least 64 random bytes and JWT lifetime 1 second to 1 hour");
    this.key = Keys.hmacShaKeyFor(decoded);
    this.expiration = expiration;
    this.issuer = issuer;
  }

  public String generateToken(String email, String userId, String role) {
    return generateToken(email, userId, role, 0);
  }

  public String generateToken(String email, String userId, String role, long authVersion) {
    var builder =
        Jwts.builder()
            .subject(email)
            .issuer(issuer)
            .audience()
            .add("quiz-platform")
            .and()
            .claim("userId", userId)
            .claim("role", role)
            .claim("type", "access")
            .claim("authVersion", authVersion)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration));
    if (privateKey != null) return builder.signWith(privateKey, Jwts.SIG.RS256).compact();
    if (key != null) return builder.signWith(key, Jwts.SIG.HS512).compact();
    throw new IllegalStateException("This service only verifies access tokens");
  }

  public Claims extractAllClaims(String token) {
    var parser = Jwts.parser().requireIssuer(issuer).require("type", "access");
    if (publicKey != null) parser.verifyWith(publicKey);
    else parser.verifyWith(key);
    var signed = parser.build().parseSignedClaims(token);
    if (publicKey != null
        && (!"RS256".equals(signed.getHeader().getAlgorithm())
            || signed.getPayload().getAudience() == null
            || !signed.getPayload().getAudience().contains("quiz-platform")))
      throw new JwtException("Invalid token algorithm or audience");
    Claims c = signed.getPayload();
    if (c.getExpiration() == null
        || c.getIssuedAt() == null
        || c.getSubject() == null
        || c.getSubject().isBlank()
        || c.get("userId", String.class) == null
        || c.get("userId", String.class).isBlank()
        || c.get("role", String.class) == null
        || !Set.of("ADMIN", "STUDENT").contains(c.get("role", String.class)))
      throw new JwtException("Invalid access token claims");
    return c;
  }

  public String extractEmail(String token) {
    return extractAllClaims(token).getSubject();
  }

  public boolean validateToken(String token, UserDetails user) {
    return extractEmail(token).equals(user.getUsername()) && user.isEnabled();
  }
}
