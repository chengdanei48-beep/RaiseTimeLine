package com.raisetimeline.controller;

import com.raisetimeline.dto.AuthResponse;
import com.raisetimeline.dto.AuthResult;
import com.raisetimeline.dto.LoginRequest;
import com.raisetimeline.dto.RegisterRequest;
import com.raisetimeline.dto.UserResponse;
import com.raisetimeline.exception.UnauthorizedException;
import com.raisetimeline.security.RefreshCookies;
import com.raisetimeline.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "認証", description = "新規登録・ログイン・トークン更新・ログアウト(F-AUTH)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshCookies refreshCookies;

  public AuthController(AuthService authService, RefreshCookies refreshCookies) {
    this.authService = authService;
    this.refreshCookies = refreshCookies;
  }

  @Operation(
      summary = "新規登録",
      description =
          "アカウントを作り、そのままログイン状態にする。リフレッシュトークンは Cookie(HttpOnly)で返す。" + "ユーザーIDは大文字小文字を区別せず重複不可。")
  @SecurityRequirements
  @ApiResponse(responseCode = "201", description = "登録した(ログイン状態)")
  @ApiResponse(
      responseCode = "409",
      description = "ユーザーIDがすでに使われている",
      content = @Content(schema = @Schema(ref = "#/components/schemas/Problem")))
  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
  }

  @Operation(
      summary = "ログイン",
      description =
          "ユーザーIDとパスワードで認証する。リフレッシュトークンは Cookie(HttpOnly)で返す。"
              + "ユーザーIDの有無が分からないよう、失敗はどちらの誤りでも同じ応答にする。")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "ログインした")
  @ApiResponse(
      responseCode = "401",
      description = "ユーザーIDまたはパスワードが正しくない",
      content = @Content(schema = @Schema(ref = "#/components/schemas/Problem")))
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return withRefreshCookie(HttpStatus.OK, authService.login(request));
  }

  /** リフレッシュトークン(Cookie)を使って、新しいアクセストークンとリフレッシュトークンを発行する。 */
  @Operation(
      summary = "トークンの更新",
      description =
          "リフレッシュトークン(Cookie)で、新しいアクセストークンとリフレッシュトークンを発行する"
              + "(使い捨て。ローテーションし、再利用は検知して失効させる)。リロード後のログイン復元にも使う。")
  @SecurityRequirements
  @ApiResponse(
      responseCode = "200",
      description = "更新した",
      content = @Content(schema = @Schema(implementation = AuthResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "リフレッシュトークンが無い・無効・失効・期限切れ(Cookie は削除される)",
      content = @Content(schema = @Schema(ref = "#/components/schemas/Problem")))
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
    try {
      return withRefreshCookie(HttpStatus.OK, authService.refresh(refreshToken));
    } catch (UnauthorizedException e) {
      // 使えない Cookie は残さない
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
          .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage()));
    }
  }

  /** リフレッシュトークンを失効させ、Cookie を削除する。アクセストークンが期限切れでも呼べる(冪等)。 */
  @Operation(
      summary = "ログアウト",
      description = "リフレッシュトークンを失効させ、Cookie を削除する。アクセストークンが期限切れでも呼べる(冪等)。")
  @SecurityRequirements
  @ApiResponse(responseCode = "204", description = "ログアウトした")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
    authService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
        .build();
  }

  @Operation(summary = "ログイン中のユーザーを取得", description = "画面の初期表示・ヘッダー用に、自分の基本情報を返す。")
  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return authService.me(Long.parseLong(jwt.getSubject()));
  }

  private ResponseEntity<AuthResponse> withRefreshCookie(HttpStatus status, AuthResult result) {
    return ResponseEntity.status(status)
        .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.refreshToken()).toString())
        .body(result.response());
  }
}
