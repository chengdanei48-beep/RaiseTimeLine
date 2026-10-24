package com.raisetimeline.controller;

import com.raisetimeline.dto.FollowUserListResponse;
import com.raisetimeline.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserSearchController {

  private final UserService userService;

  public UserSearchController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public FollowUserListResponse search(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return userService.search(Long.parseLong(jwt.getSubject()), q, cursor, limit);
  }
}
