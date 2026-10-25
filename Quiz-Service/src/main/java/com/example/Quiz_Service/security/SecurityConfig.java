package com.example.Quiz_Service.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  public SecurityConfig(JwtAuthenticationFilter filter) {
    this.jwtAuthenticationFilter = filter;
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/actuator/health",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/docs/**",
                        "/demo.html",
                        "/demo.js",
                        "/demo.css")
                    .permitAll()
                    .requestMatchers("/actuator/metrics/**", "/actuator/info")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/auth/options",
                        "/auth/register",
                        "/auth/login",
                        "/auth/session",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/request-verification",
                        "/auth/verify-email")
                    .permitAll()
                    .requestMatchers("/question/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/quiz/create")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/quiz/getQuiz/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/quiz/published", "/quiz/published/*")
                    .hasAnyRole("ADMIN", "STUDENT")
                    .requestMatchers(HttpMethod.POST, "/quiz/*/attempts")
                    .hasRole("STUDENT")
                    .requestMatchers("/attempts/**")
                    .hasRole("STUDENT")
                    .anyRequest()
                    .denyAll())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) -> {
                          res.setStatus(401);
                          res.setContentType("application/json");
                          res.getWriter()
                              .write("{\"status\":401,\"message\":\"Authentication required\"}");
                        })
                    .accessDeniedHandler(
                        (req, res, ex) -> {
                          res.setStatus(403);
                          res.setContentType("application/json");
                          res.getWriter().write("{\"status\":403,\"message\":\"Access denied\"}");
                        }))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  // A Spring filter bean must not also run outside the SecurityFilterChain.
  @Bean
  FilterRegistrationBean<JwtAuthenticationFilter> jwtRegistration(JwtAuthenticationFilter filter) {
    var registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }
}
