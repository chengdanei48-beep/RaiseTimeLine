package com.raisetimeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** imageKeys は、署名付き URL でアップロード済みの画像のキー(0〜4件。先頭が 1 枚目)。 */
public record CreatePostRequest(
    @NotNull(message = "本文を入力してください") @Size(min = 1, max = 280, message = "本文は1〜280文字で入力してください")
        String body,
    @Size(max = 4, message = "画像は4枚までです")
        List<@NotBlank(message = "画像のキーが不正です") String> imageKeys) {

  public CreatePostRequest {
    if (body != null) {
      body = body.strip();
    }
    // null の要素は空文字にして、@NotBlank の検証で 400 にする(List.copyOf は null を許さないため)
    imageKeys =
        imageKeys == null
            ? List.of()
            : List.copyOf(imageKeys.stream().map(k -> k == null ? "" : k).toList());
  }
}
