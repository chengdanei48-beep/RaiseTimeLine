package com.raisetimeline.dto;

import com.raisetimeline.dto.validation.MaxBytes;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotNull(message = "ユーザーIDを入力してください")
        @Pattern(regexp = "[A-Za-z0-9_]{3,15}", message = "ユーザーIDは3〜15文字の半角英数字と _ で入力してください")
        String username,
    @NotNull(message = "表示名を入力してください") @Size(min = 1, max = 30, message = "表示名は1〜30文字で入力してください")
        String displayName,
    @NotNull(message = "パスワードを入力してください")
        @Size(min = 8, max = 72, message = "パスワードは8〜72文字で入力してください")
        @MaxBytes(value = 72, message = "パスワードが長すぎます")
        String password) {

  /** 表示名は前後の空白を除いてから検証・保存する。ユーザーIDはトリムしない(空白はパターン不一致)。 */
  public RegisterRequest {
    if (displayName != null) {
      displayName = displayName.strip();
    }
  }

  @Override
  public String toString() {
    return "RegisterRequest{username=" + username + "}";
  }
}
