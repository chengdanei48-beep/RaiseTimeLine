package com.raisetimeline.dto;

import com.raisetimeline.entity.User;

public record UserResponse(Long id, String username, String displayName, String avatarUrl) {

  /** avatarUrl は画像機能(S3)の実装まで常に null。 */
  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), null);
  }
}
