package com.example.Quiz_Service.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(-200)
public class RequestIdFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String id = req.getHeader("X-Request-Id");
    if (id == null || !id.matches("[a-zA-Z0-9-]{1,64}")) id = UUID.randomUUID().toString();
    MDC.put("requestId", id);
    res.setHeader("X-Request-Id", id);
    final String requestId = id;
    var wrapped =
        new HttpServletRequestWrapper(req) {
          @Override
          public String getHeader(String name) {
            return "X-Request-Id".equalsIgnoreCase(name) ? requestId : super.getHeader(name);
          }

          @Override
          public java.util.Enumeration<String> getHeaders(String name) {
            return "X-Request-Id".equalsIgnoreCase(name)
                ? java.util.Collections.enumeration(java.util.List.of(requestId))
                : super.getHeaders(name);
          }

          @Override
          public java.util.Enumeration<String> getHeaderNames() {
            var names = java.util.Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(n -> n.equalsIgnoreCase("X-Request-Id")))
              names.add("X-Request-Id");
            return java.util.Collections.enumeration(names);
          }
        };
    try {
      chain.doFilter(wrapped, res);
    } finally {
      MDC.remove("requestId");
    }
  }
}
