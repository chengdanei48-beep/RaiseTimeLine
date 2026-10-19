package com.raisetimeline.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "ユーザーIDを入力してください") String username,
    @NotBlank(message = "パスワードを入力してください") String password) {

  @Override
  public String toString() {
    return "LoginRequest{username=" + username + "}";
  }
}
