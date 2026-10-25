package com.example.Quiz_Service.config;

import java.time.Clock;
import org.springframework.context.annotation.*;

@Configuration
public class ClockConfig {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
