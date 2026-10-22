package com.raisetimeline.controller;

import com.raisetimeline.dto.FollowUserListResponse;
import com.raisetimeline.dto.ProfileResponse;
import com.raisetimeline.dto.TimelineResponse;
import com.raisetimeline.service.FollowService;
import com.raisetimeline.service.PostService;
import com.raisetimeline.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{id}")
public class UserController {

  private final UserService userService;
  private final FollowService followService;
  private final PostService postService;

  public UserController(
      UserService userService, FollowService followService, PostService postService) {
    this.userService = userService;
    this.followService = followService;
    this.postService = postService;
  }

  @GetMapping
  public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return userService.profile(viewerId(jwt), id);
  }

  @GetMapping("/posts")
  public TimelineResponse posts(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return postService.userPosts(viewerId(jwt), id, cursor, limit);
  }

  @PutMapping("/follow")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void follow(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    followService.follow(viewerId(jwt), id);
  }

  @DeleteMapping("/follow")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    followService.unfollow(viewerId(jwt), id);
  }

  @GetMapping("/following")
  public FollowUserListResponse following(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return followService.following(viewerId(jwt), id, cursor, limit);
  }

  @GetMapping("/followers")
  public FollowUserListResponse followers(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return followService.followers(viewerId(jwt), id, cursor, limit);
  }

  private static long viewerId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }
}
