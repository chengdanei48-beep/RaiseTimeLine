package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** imageKeys は、署名付き URL でアップロード済みの画像のキー(0〜4件。先頭が 1 枚目)。 */
@Schema(description = "投稿の入力")
public record CreatePostRequest(
    @Schema(description = "本文。前後の空白を除いて 1〜280文字(画像のみの投稿は不可)", example = "今日はいい天気ですね")
        @NotNull(message = "本文を入力してください")
        @Size(min = 1, max = 280, message = "本文は1〜280文字で入力してください")
        String body,
    @Schema(
            description =
                "アップロード済みの画像のキー(0〜4件。先頭が 1 枚目)。`POST /api/uploads/presign` が返した `objectKey` で、"
                    + "自分のもの・S3 に存在・5MB 以下・画像形式であること。重複は不可",
            example = "[\"posts/7/3f2a0c1e-9d2b-4a61-8c53-0a7e3b1f5d24.jpg\"]")
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
