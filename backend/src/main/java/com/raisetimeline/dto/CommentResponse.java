package com.raisetimeline.dto;

import com.raisetimeline.dto.PostResponse.AuthorResponse;
import com.raisetimeline.entity.Comment;
import java.time.Instant;

public record CommentResponse(Long id, String body, Instant createdAt, AuthorResponse author) {

  public static CommentResponse from(Comment comment) {
    return new CommentResponse(
        comment.getId(),
        comment.getBody(),
        comment.getCreatedAt(),
        new AuthorResponse(
            comment.getUserId(),
            comment.getAuthorUsername(),
            comment.getAuthorDisplayName(),
            null));
  }
}
