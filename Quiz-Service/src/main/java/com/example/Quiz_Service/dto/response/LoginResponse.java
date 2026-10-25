package com.example.Quiz_Service.dto.response;

@lombok.Data
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class LoginResponse {
  private String token;
  private String userName;
  private String email;
  private String role;
}
