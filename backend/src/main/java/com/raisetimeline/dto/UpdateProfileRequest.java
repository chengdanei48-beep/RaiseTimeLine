package com.raisetimeline.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * プロフィール更新(PUT /api/users/me)。displayName・bio は常に置き換える(bio は省略・空で消す)。 avatarKey は、省略=変更なし /
 * null=アイコン削除 / 文字列=新しいアイコン。省略と null を区別するため、 セッターが呼ばれたかどうかを持つ。
 */
public class UpdateProfileRequest {

  @Schema(description = "表示名。前後の空白を除いて 1〜30文字", example = "佐藤花子")
  @NotNull(message = "表示名を入力してください")
  @Size(min = 1, max = 30, message = "表示名は1〜30文字で入力してください")
  private String displayName;

  @Schema(description = "自己紹介。0〜160文字(省略・空にすると消える)", example = "よろしくお願いします")
  @Size(max = 160, message = "自己紹介は160文字以内で入力してください")
  private String bio;

  @Schema(
      description =
          "アイコンのキー。**省略=変更なし / `null`=削除 / 文字列=新しいアイコン**。文字列は `POST /api/uploads/presign`"
              + "(`purpose=AVATAR`)が返した自分の `objectKey` で、S3 に存在し 2MB 以下の画像であること",
      nullable = true,
      example = "avatars/7/3f2a0c1e-9d2b-4a61-8c53-0a7e3b1f5d24.png")
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
  @Schema(hidden = true)
  public boolean isAvatarKeySpecified() {
    return avatarKeySpecified;
  }
}
