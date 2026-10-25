package com.example.Question_Service.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Response {
  private String id; // question id
  private String response; // user's chosen answer
}
