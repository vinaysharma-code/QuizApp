package com.example.Quiz_Service.security;

import com.example.Quiz_Service.repository.UserRepo;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
  private final UserRepo userRepo;

  public CustomUserDetailsService(UserRepo repo) {
    this.userRepo = repo;
  }

  @Override
  public UserDetails loadUserByUsername(String email) {
    var user =
        userRepo
            .findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
        .password(user.getPassword())
        .roles(user.getRole().name())
        .disabled(!user.isActive())
        .build();
  }
}
