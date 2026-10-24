package com.raisetimeline.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * プロフィール更新(PUT /api/users/me)。displayName・bio は常に置き換える(bio は省略・空で消す)。 avatarKey は、省略=変更なし /
 * null=アイコン削除 / 文字列=新しいアイコン。省略と null を区別するため、 セッターが呼ばれたかどうかを持つ。
 */
public class UpdateProfileRequest {

  @NotNull(message = "表示名を入力してください")
  @Size(min = 1, max = 30, message = "表示名は1〜30文字で入力してください")
  private String displayName;

  @Size(max = 160, message = "自己紹介は160文字以内で入力してください")
  private String bio;

  private String avatarKey;
  private boolean avatarKeySpecified;

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName == null ? null : displayName.strip();
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio == null ? null : bio.strip();
  }

  public String getAvatarKey() {
    return avatarKey;
  }

  @JsonSetter("avatarKey")
  public void setAvatarKey(String avatarKey) {
    this.avatarKey = avatarKey;
    this.avatarKeySpecified = true;
  }

  /** リクエストに avatarKey が含まれていたか(null を含む)。 */
  public boolean isAvatarKeySpecified() {
    return avatarKeySpecified;
  }
}
