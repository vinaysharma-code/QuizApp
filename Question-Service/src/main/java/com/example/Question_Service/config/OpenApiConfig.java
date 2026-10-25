package com.example.Question_Service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
  @Bean
  OpenAPI openApi() {
    return new OpenAPI()
        .info(new Info().title("Question-Service").version("1.0"))
        .servers(List.of(new Server().url("/")))
        .components(
            new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(
            new io.swagger.v3.oas.models.security.SecurityRequirement().addList("bearerAuth"));
  }
}
