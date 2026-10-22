package com.raisetimeline.controller;

import com.raisetimeline.dto.CommentListResponse;
import com.raisetimeline.dto.CommentResponse;
import com.raisetimeline.dto.CreateCommentRequest;
import com.raisetimeline.service.CommentService;
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

@RestController
@RequestMapping("/api")
public class CommentController {

  private final CommentService commentService;

  public CommentController(CommentService commentService) {
    this.commentService = commentService;
  }

  @PostMapping("/posts/{postId}/comments")
  public ResponseEntity<CommentResponse> create(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long postId,
      @Valid @RequestBody CreateCommentRequest request) {
    CommentResponse created =
        commentService.create(Long.parseLong(jwt.getSubject()), postId, request.body());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @GetMapping("/posts/{postId}/comments")
  public CommentListResponse list(
      @PathVariable long postId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return commentService.list(postId, cursor, limit);
  }

  @DeleteMapping("/comments/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    commentService.delete(Long.parseLong(jwt.getSubject()), id);
  }
}
