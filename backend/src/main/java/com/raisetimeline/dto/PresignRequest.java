package com.raisetimeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 画像アップロード用の署名付き URL の依頼。purpose=POST(投稿画像)/ AVATAR(アイコン)。 */
public record PresignRequest(
    @NotNull(message = "purpose を指定してください") Purpose purpose,
    @NotBlank(message = "contentType を指定してください") String contentType,
    @NotNull(message = "size を指定してください") @Positive(message = "size は 1 以上で指定してください") Long size) {

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
