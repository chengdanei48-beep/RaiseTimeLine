package com.raisetimeline.controller;

import com.raisetimeline.dto.FollowUserListResponse;
import com.raisetimeline.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "ユーザー検索", description = "ユーザーIDと表示名の検索(F-SEARCH)")
@RestController
@RequestMapping("/api/users")
public class UserSearchController {

  private final UserService userService;

  public UserSearchController(UserService userService) {
    this.userService = userService;
  }

  @Operation(
      summary = "ユーザーを検索",
      description =
          "ユーザーID・表示名の部分一致(大文字小文字を区別しない)。自分は除く。並びはユーザーID(小文字)の昇順。"
              + "`q` の先頭の `@` と前後の空白は無視する。`%` `_` は文字として扱う。")
  @GetMapping
  public FollowUserListResponse search(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return userService.search(Long.parseLong(jwt.getSubject()), q, cursor, limit);
  }
}
