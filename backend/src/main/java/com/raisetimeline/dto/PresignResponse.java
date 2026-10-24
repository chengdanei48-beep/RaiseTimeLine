package com.raisetimeline.dto;

import java.util.Map;

/** uploadUrl へ requiredHeaders を付けて PUT する。objectKey は保存時(投稿・プロフィール更新)に渡す。 */
public record PresignResponse(
    String uploadUrl, String objectKey, long expiresIn, Map<String, String> requiredHeaders) {

  public PresignResponse {
    requiredHeaders = Map.copyOf(requiredHeaders);
  }
}
