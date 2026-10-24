package com.raisetimeline.dto;

import com.raisetimeline.entity.User;

public record UserResponse(Long id, String username, String displayName, String avatarUrl) {

  public static UserResponse from(User user, String avatarUrl) {
    return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), avatarUrl);
  }
}
