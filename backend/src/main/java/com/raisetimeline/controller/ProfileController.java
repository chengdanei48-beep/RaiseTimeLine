package com.raisetimeline.controller;

import com.raisetimeline.dto.ProfileResponse;
import com.raisetimeline.dto.UpdateProfileRequest;
import com.raisetimeline.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 自分のプロフィールの取得・更新。常にログイン中のユーザー自身が対象で、他人を指定する経路はない。 */
@RestController
@RequestMapping("/api/users/me")
public class ProfileController {

  private final UserService userService;

  public ProfileController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
    long userId = Long.parseLong(jwt.getSubject());
    return userService.profile(userId, userId);
  }

  @PutMapping
  public ProfileResponse update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    return userService.updateProfile(Long.parseLong(jwt.getSubject()), request);
  }
}
