package com.example.Quiz_Service.config;

import feign.RequestInterceptor;
import feign.Retryer;
import org.slf4j.MDC;
import org.springframework.context.annotation.*;
import org.springframework.web.context.request.*;

@Configuration
public class FeignAuthenticationConfig {
  @Bean
  RequestInterceptor forwardAuthentication() {
    return template -> {
      if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
        String authorization = attrs.getRequest().getHeader("Authorization");
        if (authorization != null) template.header("Authorization", authorization);
      }
      String id = MDC.get("requestId");
      if (id != null) template.header("X-Request-Id", id);
    };
  }

  @Bean
  Retryer retryer() {
    return Retryer.NEVER_RETRY;
  }
}
