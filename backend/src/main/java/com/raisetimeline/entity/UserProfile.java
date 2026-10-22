package com.raisetimeline.entity;

/** プロフィール表示用。users に、投稿数・フォロー数・自分がフォロー済みかを添えたもの。 */
public class UserProfile {

  private Long id;
  private String username;
  private String displayName;
  private String bio;
  private long postCount;
  private long followingCount;
  private long followerCount;
  private boolean followedByMe;

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

  public long getPostCount() {
    return postCount;
  }

  public void setPostCount(long postCount) {
    this.postCount = postCount;
  }

  public long getFollowingCount() {
    return followingCount;
  }

  public void setFollowingCount(long followingCount) {
    this.followingCount = followingCount;
  }

  public long getFollowerCount() {
    return followerCount;
  }

  public void setFollowerCount(long followerCount) {
    this.followerCount = followerCount;
  }

  public boolean isFollowedByMe() {
    return followedByMe;
  }

  public void setFollowedByMe(boolean followedByMe) {
    this.followedByMe = followedByMe;
  }
}
