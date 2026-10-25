package com.raisetimeline.controller;

import com.raisetimeline.dto.PresignRequest;
import com.raisetimeline.dto.PresignResponse;
import com.raisetimeline.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "画像アップロード", description = "S3 へ直接アップロードするための署名付き URL(F-POST / F-PROF)")
@RestController
@RequestMapping("/api/uploads")
public class UploadController {

  private final UploadService uploadService;

  public UploadController(UploadService uploadService) {
    this.uploadService = uploadService;
  }

  @Operation(
      summary = "署名付き URL を発行",
      description =
          "画像を S3 へ直接 PUT するための URL を発行する。`requiredHeaders` を付けて `uploadUrl` へ PUT し、"
              + "返った `objectKey` を投稿・プロフィール更新で渡す。キーはサーバーが決める。"
              + "形式は JPEG・PNG・GIF・WebP、サイズは投稿画像 5MB・アイコン 2MB 以下。有効期限は 5 分。")
  @ApiResponse(
      responseCode = "429",
      description = "発行が多すぎる(1 ユーザーあたり 1 分に 30 回まで)。`Retry-After` 秒後にやり直せる",
      content = @Content(schema = @Schema(ref = "#/components/schemas/Problem")))
  @PostMapping("/presign")
  public PresignResponse presign(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PresignRequest request) {
    return uploadService.presign(Long.parseLong(jwt.getSubject()), request);
  }
}
