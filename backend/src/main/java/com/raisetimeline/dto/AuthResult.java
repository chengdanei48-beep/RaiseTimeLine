package com.raisetimeline.dto;

/** 認証成功の結果。リフレッシュトークンはボディに含めず、Cookie で返すために分けて持つ。 */
public record AuthResult(AuthResponse response, String refreshToken) {

  @Override
  public String toString() {
    return "AuthResult{response=" + response + "}";
  }
}
