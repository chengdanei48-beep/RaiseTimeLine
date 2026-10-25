package com.raisetimeline.dto;

import com.raisetimeline.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "ユーザーの基本情報")
public record UserResponse(
    @Schema(description = "ユーザーの内部 ID", example = "7") Long id,
    @Schema(description = "ユーザーID(@ハンドル)。3〜15文字の半角英数字と `_`", example = "hanako") String username,
    @Schema(description = "表示名", example = "佐藤花子") String displayName,
    @Schema(description = "アイコンの表示用の署名付き URL(期限つき)。未設定なら null") String avatarUrl) {

  public static UserResponse from(User user, String avatarUrl) {
    return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), avatarUrl);
  }
}
