package com.raisetimeline.service;

import com.raisetimeline.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 署名付き URL の発行に、ユーザーごとの回数制限をかける(固定の 1 分間の窓)。 画像は 1 投稿 4 枚までなので、通常の使い方では超えない値にしている。サーバーのメモリ上で数えるため、
 * 複数台にするときは台数分の上限になる(学習用の構成では許容する)。
 */
@Component
public class PresignRateLimiter {

  /** 1 分間に発行できる回数。 */
  public static final int LIMIT_PER_MINUTE = 30;

  private static final Duration WINDOW = Duration.ofMinutes(1);

  /** 古い窓を掃除する目安の件数(ユーザーが増えてもメモリを使い続けないため)。 */
  private static final int CLEANUP_THRESHOLD = 1000;

  private record Window(Instant start, int count) {}

  private final Clock clock;
  private final ConcurrentHashMap<Long, Window> windows = new ConcurrentHashMap<>();

  public PresignRateLimiter() {
    this(Clock.systemUTC());
  }

  PresignRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /** 発行してよければ数えて戻る。上限を超えていたら TooManyRequestsException。 */
  public void check(long userId) {
    Instant now = clock.instant();
    windows.compute(
        userId,
        (id, window) -> {
          if (window == null || !now.isBefore(window.start().plus(WINDOW))) {
            return new Window(now, 1);
          }
          if (window.count() >= LIMIT_PER_MINUTE) {
            long retryAfter =
                Math.max(1, Duration.between(now, window.start().plus(WINDOW)).toSeconds());
            throw new TooManyRequestsException(retryAfter);
          }
          return new Window(window.start(), window.count() + 1);
        });
    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.values().removeIf(window -> !now.isBefore(window.start().plus(WINDOW)));
    }
  }
}
