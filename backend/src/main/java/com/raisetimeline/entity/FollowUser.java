package com.raisetimeline.entity;

import java.time.Instant;

/** フォロー中・フォロワー一覧の1行。followedAt はフォローした日時(並びとカーソルに使う)。 */
public class FollowUser {

  private Long id;
  private String username;
  private String displayName;
  private String bio;
  private Instant followedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public Instant getFollowedAt() {
    return followedAt;
  }

  public void setFollowedAt(Instant followedAt) {
    this.followedAt = followedAt;
  }
}
