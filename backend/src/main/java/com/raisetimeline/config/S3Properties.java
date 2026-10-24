package com.raisetimeline.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 画像用 S3 の設定。endpoint を指定すると S3 互換サーバー(開発用)に、空なら AWS S3 に接続する。 accessKey / secretKey が空なら、AWS
 * の既定の認証(EC2 の IAM ロールなど)を使う。
 */
@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
    String bucket,
    String region,
    String endpoint,
    String accessKey,
    String secretKey,
    Duration putExpiry,
    Duration getExpiry) {

  public S3Properties {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("S3_BUCKET を指定してください");
    }
    if (region == null || region.isBlank()) {
      throw new IllegalStateException("AWS_REGION を指定してください");
    }
    if (putExpiry == null || putExpiry.isNegative() || putExpiry.isZero()) {
      throw new IllegalStateException("app.s3.put-expiry は正の期間で指定してください");
    }
    if (getExpiry == null || getExpiry.isNegative() || getExpiry.isZero()) {
      throw new IllegalStateException("app.s3.get-expiry は正の期間で指定してください");
    }
  }

  public boolean hasEndpoint() {
    return endpoint != null && !endpoint.isBlank();
  }

  public boolean hasStaticCredentials() {
    return accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank();
  }

  /** 認証情報(秘密鍵)をログに出さない。 */
  @Override
  public String toString() {
    return "S3Properties{bucket=" + bucket + ", region=" + region + ", endpoint=" + endpoint + "}";
  }
}
