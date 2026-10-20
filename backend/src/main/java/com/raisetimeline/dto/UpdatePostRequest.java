package com.raisetimeline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePostRequest(
    @NotNull(message = "本文を入力してください") @Size(min = 1, max = 280, message = "本文は1〜280文字で入力してください")
        String body) {

  public UpdatePostRequest {
    if (body != null) {
      body = body.strip();
    }
  }
}
