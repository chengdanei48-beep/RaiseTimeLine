package com.raisetimeline.dto;

import com.raisetimeline.dto.PostResponse.AuthorResponse;
import com.raisetimeline.entity.Comment;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "コメント")
public record CommentResponse(
    @Schema(description = "コメントの ID", example = "116") Long id,
    @Schema(description = "本文(1〜280文字)", example = "素敵ですね!") String body,
    @Schema(description = "作成日時(UTC)") Instant createdAt,
    @Schema(description = "コメントした人") AuthorResponse author) {

  public static CommentResponse from(Comment comment, String authorAvatarUrl) {
    return new CommentResponse(
        comment.getId(),
        comment.getBody(),
        comment.getCreatedAt(),
        new AuthorResponse(
            comment.getUserId(),
            comment.getAuthorUsername(),
            comment.getAuthorDisplayName(),
            authorAvatarUrl));
  }
}
