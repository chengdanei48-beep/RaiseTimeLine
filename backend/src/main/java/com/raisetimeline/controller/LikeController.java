package com.raisetimeline.controller;

import com.raisetimeline.service.LikeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{postId}/like")
public class LikeController {

  private final LikeService likeService;

  public LikeController(LikeService likeService) {
    this.likeService = likeService;
  }

  @PutMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void like(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId) {
    likeService.like(Long.parseLong(jwt.getSubject()), postId);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable long postId) {
    likeService.unlike(Long.parseLong(jwt.getSubject()), postId);
  }
}
