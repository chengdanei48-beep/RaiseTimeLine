package com.raisetimeline.controller;

import com.raisetimeline.dto.CommentListResponse;
import com.raisetimeline.dto.CommentResponse;
import com.raisetimeline.dto.CreateCommentRequest;
import com.raisetimeline.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "コメント", description = "投稿へのコメント(F-CMT)")
@RestController
@RequestMapping("/api")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @Operation(summary = "コメントを投稿", description = "本文(1〜280文字)。自分の投稿にも、他人の投稿にもできる。コメントへのコメントは不可。")
  @ApiResponse(responseCode = "201", description = "投稿した")
  @PostMapping("/posts/{postId}/comments")
  public ResponseEntity<CommentResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long postId,
      @Valid @RequestBody CreateCommentRequest request) {
    CommentResponse created =
        commentService.create(Long.parseLong(jwt.getSubject()), postId, request.body());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @Operation(summary = "コメント一覧", description = "古い順(作成日時の昇順、同時刻は ID の昇順)。削除済みは含まない。")
  @GetMapping("/posts/{postId}/comments")
  public CommentListResponse list(
      @PathVariable long postId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return commentService.list(postId, cursor, limit);
  }

  @Operation(summary = "コメントを削除", description = "論理削除。作成者のみ。")
  @ApiResponse(
      responseCode = "403",
      description = "他人のコメント",
      content = @Content(schema = @Schema(ref = "#/components/schemas/Problem")))
  @DeleteMapping("/comments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    commentService.delete(Long.parseLong(jwt.getSubject()), id);
  }
}
