package com.raisetimeline.dto;

import com.raisetimeline.entity.Post;
import java.time.Instant;
import java.util.List;

/** インプレッション数などの閲覧数系フィールドは持たない(要件)。 */
public record PostResponse(
    Long id,
    String body,
    Instant createdAt,
    Instant updatedAt,
    AuthorResponse author,
    List<ImageResponse> images,
    long likeCount,
    long commentCount,
    boolean likedByMe) {

  public PostResponse {
    images = List.copyOf(images);
  }

  public record AuthorResponse(Long id, String username, String displayName, String avatarUrl) {}

  /** url は表示用の署名付き URL(期限つき)。sortOrder は 0〜3。 */
  public record ImageResponse(String url, int sortOrder) {}

  public static PostResponse of(
      Post post,
      List<ImageResponse> images,
      long likeCount,
      long commentCount,
      boolean likedByMe,
      String authorAvatarUrl) {
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
        images,
        likeCount,
        commentCount,
        likedByMe);
  }
}
