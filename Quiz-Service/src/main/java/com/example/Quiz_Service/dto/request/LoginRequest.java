package com.example.Quiz_Service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {
  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(max = 72)
  private String password;
}
