package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 画像アップロード用の署名付き URL の依頼。purpose=POST(投稿画像)/ AVATAR(アイコン)。 */
@Schema(description = "署名付き URL の依頼")
public record PresignRequest(
    @Schema(description = "用途。`POST`=投稿画像(5MB 以下)、`AVATAR`=アイコン(2MB 以下)")
        @NotNull(message = "purpose を指定してください")
        Purpose purpose,
    @Schema(
            description = "画像の形式。`image/jpeg`・`image/png`・`image/gif`・`image/webp` のいずれか",
            example = "image/jpeg")
        @NotBlank(message = "contentType を指定してください")
        String contentType,
    @Schema(description = "画像のバイト数", example = "204800")
        @NotNull(message = "size を指定してください")
        @Positive(message = "size は 1 以上で指定してください")
        Long size) {

  public enum Purpose {
    POST("posts"),
    AVATAR("avatars");

    private final String keyPrefix;

    Purpose(String keyPrefix) {
      this.keyPrefix = keyPrefix;
    }

    /** S3 のキーの先頭(例: avatars)。 */
    public String keyPrefix() {
      return keyPrefix;
    }
  }
}
