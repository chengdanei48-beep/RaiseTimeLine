package com.raisetimeline.dto;

public record AuthResponse(
    String accessToken, String tokenType, long expiresIn, UserResponse user) {

  public static AuthResponse bearer(String accessToken, long expiresIn, UserResponse user) {
    return new AuthResponse(accessToken, "Bearer", expiresIn, user);
  }

  @Override
  public String toString() {
    return "AuthResponse{user=" + user + "}";
  }
}
