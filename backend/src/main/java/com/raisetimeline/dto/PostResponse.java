package com.raisetimeline.dto;

import com.raisetimeline.entity.Post;
import java.time.Instant;

/**
 * インプレッション数などの閲覧数系フィールドは持たない(要件)。
 *
 * <p>scheduled は「公開日時(createdAt)がまだ先」の予約中であることを表す。
 */
public record PostResponse(
    Long id,
    String body,
    Instant createdAt,
    Instant updatedAt,
    boolean scheduled,
    AuthorResponse author) {

  /** avatarUrl は画像機能(S3)の実装まで常に null。 */
  public record AuthorResponse(Long id, String username, String displayName, String avatarUrl) {}

  public static PostResponse from(Post post) {
    return new PostResponse(
        post.getId(),
        post.getBody(),
        post.getCreatedAt(),
        post.getUpdatedAt(),
        post.getCreatedAt().isAfter(Instant.now()),
        new AuthorResponse(
            post.getUserId(), post.getAuthorUsername(), post.getAuthorDisplayName(), null));
  }
}
