package com.raisetimeline.dto;

import com.raisetimeline.entity.Post;
import java.time.Instant;

/** インプレッション数などの閲覧数系フィールドは持たない(要件)。 */
public record PostResponse(
    Long id,
    String body,
    Instant createdAt,
    Instant updatedAt,
    AuthorResponse author,
    long likeCount,
    long commentCount,
    boolean likedByMe) {

  public record AuthorResponse(Long id, String username, String displayName, String avatarUrl) {}

  public static PostResponse of(
      Post post, long likeCount, long commentCount, boolean likedByMe, String authorAvatarUrl) {
    return new PostResponse(
        post.getId(),
        post.getBody(),
        post.getCreatedAt(),
        post.getUpdatedAt(),
        new AuthorResponse(
            post.getUserId(),
            post.getAuthorUsername(),
            post.getAuthorDisplayName(),
            authorAvatarUrl),
        likeCount,
        commentCount,
        likedByMe);
  }
}
