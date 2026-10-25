package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "ログインの入力")
public record LoginRequest(
    @Schema(description = "ユーザーID(大文字小文字を区別しない)", example = "hanako")
        @NotBlank(message = "ユーザーIDを入力してください")
        String username,
    @Schema(description = "パスワード", example = "password123", format = "password")
        @NotBlank(message = "パスワードを入力してください")
        String password) {

  @Override
  public String toString() {
    return "LoginRequest{username=" + username + "}";
  }
}
