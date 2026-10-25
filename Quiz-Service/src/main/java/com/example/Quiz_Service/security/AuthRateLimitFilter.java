package com.example.Quiz_Service.security;

import com.example.Quiz_Service.service.BrowserSessions;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Shared PostgreSQL counters, including direct-service requests. Does not trust forwarded IP
 * headers.
 */
@Component
@Order(1)
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private final JdbcTemplate db;

  public AuthRateLimitFilter(JdbcTemplate db) {
    this.db = db;
  }

  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String path = req.getServletPath();
    if ("POST".equals(req.getMethod())
        && java.util.Set.of(
                "/auth/login",
                "/auth/session",
                "/auth/register",
                "/auth/forgot-password",
                "/auth/request-verification",
                "/auth/reset-password",
                "/auth/verify-email")
            .contains(path)) {
      // Behind Gateway this intentionally provides a shared ceiling as well as direct-IP
      // protection.
      String bucket = BrowserSessions.hash(path + ":" + req.getRemoteAddr());
      int limit = path.equals("/auth/register") ? 30 : 120;
      Integer count =
          db.queryForObject(
              "INSERT INTO auth_rate_limits(bucket,window_start,hits) VALUES(?,now(),1) ON"
                  + " CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN"
                  + " auth_rate_limits.window_start<now()-interval '1 minute' THEN 1 ELSE"
                  + " auth_rate_limits.hits+1 END,window_start=CASE WHEN"
                  + " auth_rate_limits.window_start<now()-interval '1 minute' THEN now() ELSE"
                  + " auth_rate_limits.window_start END RETURNING hits",
              Integer.class,
              bucket);
      if (count != null && count > limit) {
        res.setStatus(429);
        res.setHeader("Retry-After", "60");
        res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"Too many requests; retry in a minute\"}");
        return;
      }
    }
    chain.doFilter(req, res);
  }
}
