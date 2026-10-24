package com.raisetimeline.dto;

import com.raisetimeline.entity.UserProfile;

/** インプレッション数などの閲覧数系フィールドは持たない(要件)。 */
public record ProfileResponse(
    Long id,
    String username,
    String displayName,
    String bio,
    String avatarUrl,
    long postCount,
    long followingCount,
    long followerCount,
    boolean followedByMe,
    boolean isMe) {

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
