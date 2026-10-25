package com.raisetimeline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** uploadUrl へ requiredHeaders を付けて PUT する。objectKey は保存時(投稿・プロフィール更新)に渡す。 */
@Schema(description = "署名付き PUT URL。`requiredHeaders` を付けて `uploadUrl` へ PUT し、`objectKey` を保存時に渡す")
public record PresignResponse(
    @Schema(description = "S3 へ直接 PUT する署名付き URL") String uploadUrl,
    @Schema(
            description = "サーバーが決めたオブジェクトキー",
            example = "posts/7/3f2a0c1e-9d2b-4a61-8c53-0a7e3b1f5d24.jpg")
        String objectKey,
    @Schema(description = "`uploadUrl` の有効期間(秒)", example = "300") long expiresIn,
    @Schema(description = "PUT のときに必ず付けるヘッダー", example = "{\"Content-Type\": \"image/jpeg\"}")
        Map<String, String> requiredHeaders) {

  public PresignResponse {
    requiredHeaders = Map.copyOf(requiredHeaders);
  }
}
