package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "ログイン・登録・トークン更新の結果。リフレッシュトークンは Cookie(HttpOnly)で別に返る")
public record AuthResponse(
    @Schema(description = "アクセストークン(JWT)。`Authorization: Bearer` で送る") String accessToken,
    @Schema(description = "トークンの種類(常に Bearer)", example = "Bearer") String tokenType,
    @Schema(description = "アクセストークンの有効期間(秒)", example = "900") long expiresIn,
    @Schema(description = "ログインしたユーザー") UserResponse user) {

  public static AuthResponse bearer(String accessToken, long expiresIn, UserResponse user) {
    return new AuthResponse(accessToken, "Bearer", expiresIn, user);
  }

  @Override
  public String toString() {
    return "AuthResponse{user=" + user + "}";
  }
}
