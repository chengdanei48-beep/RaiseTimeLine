package com.raisetimeline.dto;

/** followedByMe は、自分自身の行では null(フォローボタンを出さない)。 */
public record FollowUserResponse(
    Long id,
    String username,
    String displayName,
    String avatarUrl,
    String bio,
    Boolean followedByMe) {}
