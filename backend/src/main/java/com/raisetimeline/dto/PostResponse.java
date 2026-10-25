package com.raisetimeline.dto;

import com.raisetimeline.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** インプレッション数などの閲覧数系フィールドは持たない(要件)。 */
@Schema(description = "投稿(画像・いいね数・コメント数・自分のいいね済みを含む)。閲覧数・インプレッションの項目は無い")
public record PostResponse(
    @Schema(description = "投稿の ID", example = "101") Long id,
    @Schema(description = "本文(1〜280文字)", example = "今日はいい天気ですね") String body,
    @Schema(description = "作成日時(UTC)") Instant createdAt,
    @Schema(description = "最終更新日時(UTC)。`createdAt` より後なら「編集済み」") Instant updatedAt,
    @Schema(description = "投稿した人") AuthorResponse author,
    @Schema(description = "画像(表示順。最大4枚。無ければ空)") List<ImageResponse> images,
    @Schema(description = "いいね数", example = "12") long likeCount,
    @Schema(description = "コメント数(削除済みを除く)", example = "3") long commentCount,
    @Schema(description = "自分がいいね済みか") boolean likedByMe) {

  public PostResponse {
    images = List.copyOf(images);
  }

  @Schema(description = "投稿・コメントした人")
  public record AuthorResponse(
      @Schema(description = "ユーザーの内部 ID", example = "7") Long id,
      @Schema(description = "ユーザーID(@ハンドル)", example = "hanako") String username,
      @Schema(description = "表示名", example = "佐藤花子") String displayName,
      @Schema(description = "アイコンの表示用の署名付き URL(期限つき)。未設定なら null") String avatarUrl) {}

  /** url は表示用の署名付き URL(期限つき)。sortOrder は 0〜3。 */
  @Schema(description = "投稿の画像")
  public record ImageResponse(
      @Schema(description = "表示用の署名付き URL(有効期限 15 分)") String url,
      @Schema(description = "表示順(0〜3)", example = "0") int sortOrder) {}

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
