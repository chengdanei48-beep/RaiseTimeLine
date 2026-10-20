package com.raisetimeline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** scheduledAt を指定すると予約投稿になる(省略で即時投稿)。 */
public record CreatePostRequest(
    @NotNull(message = "本文を入力してください") @Size(min = 1, max = 280, message = "本文は1〜280文字で入力してください")
        String body,
    Instant scheduledAt) {

  public CreatePostRequest {
    if (body != null) {
      body = body.strip();
    }
  }
}
