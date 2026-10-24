package com.raisetimeline.controller;

import com.raisetimeline.dto.CreatePostRequest;
import com.raisetimeline.dto.PostResponse;
import com.raisetimeline.dto.TimelineResponse;
import com.raisetimeline.dto.UpdatePostRequest;
import com.raisetimeline.service.PostService;
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

@RestController
@RequestMapping("/api")
public class PostController {

  private final PostService postService;

  public PostController(PostService postService) {
    this.postService = postService;
  }

  @PostMapping("/posts")
  public ResponseEntity<PostResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePostRequest request) {
    PostResponse created = postService.create(userId(jwt), request.body(), request.imageKeys());
    return ResponseEntity.created(URI.create("/api/posts/" + created.id())).body(created);
  }

  @GetMapping("/posts/{id}")
  public PostResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return postService.get(userId(jwt), id);
  }

  @PatchMapping("/posts/{id}")
  public PostResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @Valid @RequestBody UpdatePostRequest request) {
    return postService.update(userId(jwt), id, request.body());
  }

  @DeleteMapping("/posts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    postService.delete(userId(jwt), id);
  }

  @GetMapping("/timeline")
  public TimelineResponse timeline(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return postService.timeline(userId(jwt), scope, cursor, limit);
  }

  private static long userId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
