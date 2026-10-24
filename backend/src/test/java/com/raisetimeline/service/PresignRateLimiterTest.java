package com.raisetimeline.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.raisetimeline.exception.TooManyRequestsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PresignRateLimiterTest {

  /** 進められる時計(テスト用)。 */
  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-24T00:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private final MutableClock clock = new MutableClock();
  private final PresignRateLimiter limiter = new PresignRateLimiter(clock);

  @Test
  void 上限までは通り超えると例外() {
    for (int i = 0; i < PresignRateLimiter.LIMIT_PER_MINUTE; i++) {
      limiter.check(1);
    }

    assertThatThrownBy(() -> limiter.check(1))
        .isInstanceOfSatisfying(
            TooManyRequestsException.class,
            e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 60L));
  }

  @Test
  void 一分たつと数え直す() {
    for (int i = 0; i < PresignRateLimiter.LIMIT_PER_MINUTE; i++) {
      limiter.check(1);
    }
    clock.advance(Duration.ofMinutes(1));

    assertThatCode(() -> limiter.check(1)).doesNotThrowAnyException();
  }

  @Test
  void ユーザーごとに数える() {
    for (int i = 0; i < PresignRateLimiter.LIMIT_PER_MINUTE; i++) {
      limiter.check(1);
    }

    assertThatCode(() -> limiter.check(2)).doesNotThrowAnyException();
    assertThatThrownBy(() -> limiter.check(1)).isInstanceOf(TooManyRequestsException.class);
  }

  @Test
  void 窓の途中ならやり直せるまでの秒数が減る() {
    for (int i = 0; i < PresignRateLimiter.LIMIT_PER_MINUTE; i++) {
      limiter.check(1);
    }
    clock.advance(Duration.ofSeconds(45));

    assertThatThrownBy(() -> limiter.check(1))
        .isInstanceOfSatisfying(
            TooManyRequestsException.class,
            e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(15L));
  }
}
