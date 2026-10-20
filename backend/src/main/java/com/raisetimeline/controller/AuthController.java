package com.raisetimeline.controller;

import com.raisetimeline.dto.AuthResponse;
import com.raisetimeline.dto.AuthResult;
import com.raisetimeline.dto.LoginRequest;
import com.raisetimeline.dto.RegisterRequest;
import com.raisetimeline.dto.UserResponse;
import com.raisetimeline.exception.UnauthorizedException;
import com.raisetimeline.security.RefreshCookies;
import com.raisetimeline.service.AuthService;
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

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshCookies refreshCookies;

  public AuthController(AuthService authService, RefreshCookies refreshCookies) {
    this.authService = authService;
    this.refreshCookies = refreshCookies;
  }

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return withRefreshCookie(HttpStatus.CREATED, authService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return withRefreshCookie(HttpStatus.OK, authService.login(request));
  }

  /** リフレッシュトークン(Cookie)を使って、新しいアクセストークンとリフレッシュトークンを発行する。 */
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
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
    authService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
        .build();
  }

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
