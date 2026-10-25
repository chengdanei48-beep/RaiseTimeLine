package com.raisetimeline.controller;

import com.raisetimeline.dto.ProfileResponse;
import com.raisetimeline.dto.UpdateProfileRequest;
import com.raisetimeline.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 自分のプロフィールの取得・更新。常にログイン中のユーザー自身が対象で、他人を指定する経路はない。 */
@Tag(name = "プロフィール(自分)", description = "自分のプロフィールの取得・更新(F-PROF)")
@RestController
@RequestMapping("/api/users/me")
public class ProfileController {

  private final UserService userService;

  public ProfileController(UserService userService) {
    this.userService = userService;
  }

  @Operation(summary = "自分のプロフィールを取得", description = "`GET /api/users/{id}` と同じ形式。`isMe` は true。")
  @GetMapping
  public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
    long userId = Long.parseLong(jwt.getSubject());
    return userService.profile(userId, userId);
  }

  @Operation(
      summary = "自分のプロフィールを更新",
      description =
          "表示名(1〜30文字)・自己紹介(0〜160文字)を置き換える。ユーザーIDは変更できない。"
              + "アイコンは `avatarKey` で変更する(省略=変更なし / `null`=削除 / 文字列=アップロード済みの新しい画像)。")
  @PutMapping
  public ProfileResponse update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    return userService.updateProfile(Long.parseLong(jwt.getSubject()), request);
  }
}
