package com.raisetimeline.controller;

import com.raisetimeline.dto.CreatePostRequest;
import com.raisetimeline.dto.PostResponse;
import com.raisetimeline.dto.TimelineResponse;
import com.raisetimeline.dto.UpdatePostRequest;
import com.raisetimeline.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "投稿・タイムライン", description = "投稿の作成・取得・編集・削除、タイムライン(F-POST / F-TL)")
@RestController
@RequestMapping("/api")
public class PostController {

  private final PostService postService;

  public PostController(PostService postService) {
    this.postService = postService;
  }

  @Operation(
      summary = "投稿を作成",
      description =
          "本文(1〜280文字)と、アップロード済みの画像(最大4枚)で投稿する。画像のみの投稿は不可。"
              + "画像は先に `POST /api/uploads/presign` で署名付き URL を得て S3 へ PUT し、返った `objectKey` を "
              + "`imageKeys` に渡す(自分のキー・S3 に存在・5MB 以下・画像形式であること)。")
  @ApiResponse(responseCode = "201", description = "作成した(`Location` に `/api/posts/{id}`)")
  @PostMapping("/posts")
  public ResponseEntity<PostResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePostRequest request) {
    PostResponse created = postService.create(userId(jwt), request.body(), request.imageKeys());
    return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
  }

  @Operation(summary = "投稿を取得", description = "投稿1件(画像・いいね数・コメント数・自分のいいね済みを含む)。削除済みは 404。")
  @GetMapping("/posts/{id}")
  public PostResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return postService.get(userId(jwt), id);
  }

  @Operation(summary = "投稿を編集", description = "本文だけを変更できる(画像は変更不可)。本人のみ。")
  @ApiResponse(
      responseCode = "403",
      description = "他人の投稿",
      content = @Content(schema = @Schema(ref = "#/components/schemas/Problem")))
  @PatchMapping("/posts/{id}")
  public PostResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @Valid @RequestBody UpdatePostRequest request) {
    return postService.update(userId(jwt), id, request.body());
  }

  @Operation(summary = "投稿を削除", description = "論理削除。コメント・いいね・画像も表示されなくなる。本人のみ。")
  @ApiResponse(
      responseCode = "403",
      description = "他人の投稿",
      content = @Content(schema = @Schema(ref = "#/components/schemas/Problem")))
  @DeleteMapping("/posts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    postService.delete(userId(jwt), id);
  }

  @Operation(
      summary = "タイムラインを取得",
      description = "新しい順(作成日時、同時刻は ID の降順)。カーソル方式なので、読み込み中に投稿が増えても重複・欠落しない。")
  @GetMapping("/timeline")
  public TimelineResponse timeline(
      @AuthenticationPrincipal Jwt jwt,
      @Parameter(
              description = "`following`(自分+フォロー中)または `all`(全体)。省略時は `all`",
              schema =
                  @Schema(
                      allowableValues = {"following", "all"},
                      defaultValue = "all"))
          @RequestParam(required = false)
          String scope,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return postService.timeline(userId(jwt), scope, cursor, limit);
  }

  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
