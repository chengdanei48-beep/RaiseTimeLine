package com.raisetimeline.security;

import com.raisetimeline.config.RefreshProperties;
import com.raisetimeline.entity.RefreshToken;
import com.raisetimeline.exception.UnauthorizedException;
import com.raisetimeline.repository.RefreshTokenMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * リフレッシュトークンの発行・ローテーション・失効。呼び出し側のトランザクション内で使う。
 *
 * <p>トークンは 256 ビットの乱数で、DB には SHA-256 ハッシュだけを保存する。
 */
@Service
public class RefreshTokenService {

  private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final int TOKEN_BYTES = 32;

  private final RefreshTokenMapper mapper;
  private final RefreshProperties properties;
  private final SecureRandom random = new SecureRandom();

  public RefreshTokenService(RefreshTokenMapper mapper, RefreshProperties properties) {
    this.mapper = mapper;
    this.properties = properties;
  }

  /** 新しいトークンを発行して返す(平文はここでしか得られない)。 */
  public String issue(long userId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    random.nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

    RefreshToken entity = new RefreshToken();
    entity.setUserId(userId);
    entity.setTokenHash(hash(token));
    entity.setExpiresAt(Instant.now().plus(properties.expiration()));
    mapper.insert(entity);
    return token;
  }

  /**
   * 提示されたトークンを失効させ、持ち主のユーザーIDを返す(ローテーションの前半)。
   *
   * <p>無効・期限切れ・失効済みは {@link UnauthorizedException}。失効済みの再提示は盗難の可能性があるため、
   * 直近の猶予内(複数タブの同時リフレッシュ)を除き、そのユーザーの全トークンを失効させる。
   */
  public long consume(String token) {
    if (token == null || token.isBlank()) {
      throw new UnauthorizedException();
    }
    Instant now = Instant.now();
    RefreshToken stored =
        mapper.findByHashForUpdate(hash(token)).orElseThrow(UnauthorizedException::new);

    if (stored.getRevokedAt() != null) {
      if (stored.getRevokedAt().plus(properties.reuseGrace()).isBefore(now)) {
        mapper.revokeAllByUser(stored.getUserId(), now);
        // 盗難の可能性。セキュリティ監視のアラート対象
        LOG.atWarn()
            .addKeyValue("event", "refresh_token_reuse_detected")
            .addKeyValue("user_id", stored.getUserId())
            .log("失効済みリフレッシュトークンの再提示を検知(全トークンを失効)");
      }
      throw new UnauthorizedException();
    }
    if (stored.getExpiresAt().isBefore(now)) {
      throw new UnauthorizedException();
    }
    mapper.revoke(stored.getId(), now);
    mapper.deleteExpiredByUser(stored.getUserId(), now);
    return stored.getUserId();
  }

  /** ログアウト用。存在しない・失効済みでも何もしない(冪等)。 */
  public void revoke(String token) {
    if (token != null && !token.isBlank()) {
      mapper.revokeByHash(hash(token), Instant.now());
    }
  }

  private static String hash(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
