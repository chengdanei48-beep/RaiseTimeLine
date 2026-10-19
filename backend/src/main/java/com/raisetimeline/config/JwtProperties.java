package com.raisetimeline.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {

  private static final int MIN_SECRET_BYTES = 32;

  public JwtProperties {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException("JWT_SECRET は 32 バイト以上で指定してください");
    }
    if (expiration == null || expiration.isNegative() || expiration.isZero()) {
      throw new IllegalStateException("app.jwt.expiration は正の期間で指定してください");
    }
  }

  @Override
  public String toString() {
    return "JwtProperties{expiration=" + expiration + "}";
  }
}
