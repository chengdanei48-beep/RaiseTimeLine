package com.raisetimeline.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * リフレッシュトークンの設定。
 *
 * @param expiration 有効期間
 * @param cookieSecure Cookie に Secure 属性を付けるか(HTTPS 前提の本番では true)
 * @param reuseGrace ローテーション直後の旧トークン再提示を「同時リフレッシュ」とみなす猶予
 */
@ConfigurationProperties(prefix = "app.refresh")
public record RefreshProperties(
    Duration expiration, boolean cookieSecure, @DefaultValue("10s") Duration reuseGrace) {

  public RefreshProperties {
    if (expiration == null || expiration.isNegative() || expiration.isZero()) {
      throw new IllegalStateException("app.refresh.expiration は正の期間で指定してください");
    }
    if (reuseGrace == null || reuseGrace.isNegative()) {
      throw new IllegalStateException("app.refresh.reuse-grace は 0 以上の期間で指定してください");
    }
  }
}
