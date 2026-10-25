package com.raisetimeline.controller;

import com.raisetimeline.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "いいね", description = "いいね・取り消し(F-LIKE)")
@RestController
@RequestMapping("/api/posts/{postId}/like")
public class LikeController {

  private final LikeService likeService;

  public LikeController(LikeService likeService) {
    this.likeService = likeService;
  }

  @Operation(summary = "いいね", description = "冪等(すでにいいね済みでも 204)。自分の投稿にもできる。")
  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void like(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId) {
    likeService.like(Long.parseLong(jwt.getSubject()), postId);
  }

  @Operation(summary = "いいねを取り消す", description = "冪等(いいねしていなくても 204)。")
  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId) {
    likeService.unlike(Long.parseLong(jwt.getSubject()), postId);
  }
}
