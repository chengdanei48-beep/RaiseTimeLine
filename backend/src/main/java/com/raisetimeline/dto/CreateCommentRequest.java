package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "コメントの入力")
public record CreateCommentRequest(
    @Schema(description = "本文。前後の空白を除いて 1〜280文字", example = "素敵ですね!")
        @NotNull(message = "本文を入力してください")
        @Size(min = 1, max = 280, message = "本文は1〜280文字で入力してください")
        String body) {

  public CreateCommentRequest {
    if (body != null) {
      body = body.strip();
    }
  }
}
