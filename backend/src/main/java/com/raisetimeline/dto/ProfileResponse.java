package com.raisetimeline.dto;

import com.raisetimeline.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;

/** インプレッション数などの閲覧数系フィールドは持たない(要件)。 */
@Schema(description = "プロフィール。閲覧数・インプレッションの項目は無い")
public record ProfileResponse(
    @Schema(description = "ユーザーの内部 ID", example = "7") Long id,
    @Schema(description = "ユーザーID(@ハンドル)。変更できない", example = "hanako") String username,
    @Schema(description = "表示名", example = "佐藤花子") String displayName,
    @Schema(description = "自己紹介(0〜160文字)。未設定なら null", example = "よろしくお願いします") String bio,
    @Schema(description = "アイコンの表示用の署名付き URL(期限つき)。未設定なら null") String avatarUrl,
    @Schema(description = "投稿数(削除済みを除く)", example = "24") long postCount,
    @Schema(description = "フォロー中の人数", example = "15") long followingCount,
    @Schema(description = "フォロワーの人数", example = "32") long followerCount,
    @Schema(description = "自分がフォロー済みか(自分自身のときは常に false)") boolean followedByMe,
    @Schema(description = "自分自身か") boolean isMe) {

  /** 自分自身は followedByMe を false にする。 */
  public static ProfileResponse of(UserProfile profile, long viewerId, String avatarUrl) {
    boolean me = profile.getId() == viewerId;
    return new ProfileResponse(
        profile.getId(),
        profile.getUsername(),
        profile.getDisplayName(),
        profile.getBio(),
        avatarUrl,
        profile.getPostCount(),
        profile.getFollowingCount(),
        profile.getFollowerCount(),
        !me && profile.isFollowedByMe(),
        me);
  }
}
