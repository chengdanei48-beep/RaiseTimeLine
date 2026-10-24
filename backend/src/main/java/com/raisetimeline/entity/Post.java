package com.raisetimeline.entity;

import java.time.Instant;

public class Post {

  private Long id;
  private Long userId;
  private String body;
  private Instant createdAt;
  private Instant updatedAt;

  /** 取得時に users と結合して埋める。 */
  private String authorUsername;

  private String authorDisplayName;
  private String authorAvatarKey;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getAuthorUsername() {
    return authorUsername;
  }

  public void setAuthorUsername(String authorUsername) {
    this.authorUsername = authorUsername;
  }

  public String getAuthorDisplayName() {
    return authorDisplayName;
  }

  public void setAuthorDisplayName(String authorDisplayName) {
    this.authorDisplayName = authorDisplayName;
  }

  public String getAuthorAvatarKey() {
    return authorAvatarKey;
  }

  public void setAuthorAvatarKey(String authorAvatarKey) {
    this.authorAvatarKey = authorAvatarKey;
  }
}
