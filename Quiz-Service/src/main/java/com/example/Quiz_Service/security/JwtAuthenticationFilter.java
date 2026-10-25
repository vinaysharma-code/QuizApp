package com.example.Quiz_Service.security;

import io.jsonwebtoken.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final com.example.Quiz_Service.repository.UserRepo userRepo;
  private final CustomUserDetailsService customUserDetailsService;

  public JwtAuthenticationFilter(
      JwtService jwtService,
      CustomUserDetailsService customUserDetailsService,
      com.example.Quiz_Service.repository.UserRepo userRepo) {
    this.jwtService = jwtService;
    this.userRepo = userRepo;
    this.customUserDetailsService = customUserDetailsService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null) {
      try {
        if (!header.startsWith("Bearer ") || header.length() <= 7)
          throw new BadCredentialsException("Invalid Authorization header");
        String token = header.substring(7);
        Claims claims = jwtService.extractAllClaims(token);
        var stored =
            userRepo
                .findByEmail(claims.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Invalid user"));
        Number version = claims.get("authVersion", Number.class);
        if ((version == null ? 0 : version.longValue()) != stored.getAuthVersion())
          throw new BadCredentialsException("Session revoked");
        UserDetails user = customUserDetailsService.loadUserByUsername(claims.getSubject());
        if (!jwtService.validateToken(token, user))
          throw new BadCredentialsException("Invalid user");
        var authentication =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (JwtException
          | IllegalArgumentException
          | org.springframework.security.core.AuthenticationException ex) {
        SecurityContextHolder.clearContext();
        response.setStatus(401);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"message\":\"Invalid or expired token\"}");
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
