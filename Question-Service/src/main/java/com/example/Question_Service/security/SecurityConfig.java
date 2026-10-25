package com.example.Question_Service.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
                    .requestMatchers("/question/**")
                    .hasRole("ADMIN")
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

  // This resource service accepts signed bearer tokens, never local passwords.
  @Bean
  org.springframework.security.core.userdetails.UserDetailsService tokenOnlyUsers() {
    return username -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "Bearer token required");
    };
  }
}
