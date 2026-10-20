package com.raisetimeline.security;

import com.raisetimeline.config.RefreshProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** リフレッシュトークンの Cookie。JS から読めず、認証 API のパスにだけ送られる。 */
@Component
public class RefreshCookies {

  public static final String NAME = "refresh_token";
  private static final String PATH = "/api/auth";

  private final RefreshProperties properties;

  public RefreshCookies(RefreshProperties properties) {
    this.properties = properties;
  }

  public ResponseCookie issue(String refreshToken) {
    return base(refreshToken).maxAge(properties.expiration()).build();
  }

  public ResponseCookie clear() {
    return base("").maxAge(0).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Strict")
        .path(PATH);
  }
}
