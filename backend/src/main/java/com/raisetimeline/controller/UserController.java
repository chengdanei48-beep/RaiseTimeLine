package com.raisetimeline.controller;

import com.raisetimeline.dto.FollowUserListResponse;
import com.raisetimeline.dto.ProfileResponse;
import com.raisetimeline.dto.TimelineResponse;
import com.raisetimeline.service.FollowService;
import com.raisetimeline.service.PostService;
import com.raisetimeline.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "ユーザー・フォロー", description = "プロフィール、ユーザー別の投稿、フォロー・フォロワー(F-PROF / F-FOLLOW)")
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

  @Operation(
      summary = "プロフィールを取得",
      description =
          "投稿数(削除済みを除く)・フォロー数・フォロワー数・自分がフォロー済みか・自分自身かを含む。" + "`me` は使えない(自分は `GET /api/users/me`)。")
  @GetMapping
  public ProfileResponse profile(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    return userService.profile(viewerId(jwt), id);
  }

  @Operation(summary = "ユーザーの投稿一覧", description = "そのユーザーの投稿。形式・並び・ページングはタイムラインと同じ。")
  @GetMapping("/posts")
  public TimelineResponse posts(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return postService.userPosts(viewerId(jwt), id, cursor, limit);
  }

  @Operation(
      summary = "フォローする",
      description = "承認は不要。冪等(フォロー済みでも 204)。自分自身は 400。フォロー直後から、相手の過去の投稿も「フォロー中」に出る。")
  @PutMapping("/follow")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void follow(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    followService.follow(viewerId(jwt), id);
  }

  @Operation(summary = "フォローを解除する", description = "冪等(フォローしていなくても 204)。")
  @DeleteMapping("/follow")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
    followService.unfollow(viewerId(jwt), id);
  }

  @Operation(
      summary = "フォロー中の一覧",
      description = "新しくフォローした順。`followedByMe` は自分の行では null(フォローボタンを出さない)。")
  @GetMapping("/following")
  public FollowUserListResponse following(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable long id,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit) {
    return followService.following(viewerId(jwt), id, cursor, limit);
  }

  @Operation(
      summary = "フォロワーの一覧",
      description = "新しくフォローされた順。`followedByMe` は自分の行では null(フォローボタンを出さない)。")
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
