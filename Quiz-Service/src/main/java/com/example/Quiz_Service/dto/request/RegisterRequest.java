package com.example.Quiz_Service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
  public enum AccountType { STUDENT, CREATOR }

  @NotNull
  private AccountType accountType = AccountType.STUDENT;

  @NotBlank
  @Email
  @Size(max = 254)
  private String email;

  @NotBlank
  @Size(min = 8, max = 72)
  private String password;

  @NotBlank
  @Size(max = 100)
  private String userName;
}
