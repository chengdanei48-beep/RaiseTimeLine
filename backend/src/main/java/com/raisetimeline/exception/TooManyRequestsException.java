package com.raisetimeline.exception;

/** 短時間に操作が多すぎる(429)。retryAfterSeconds 秒後にやり直せる。 */
public class TooManyRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyRequestsException(long retryAfterSeconds) {
    super("操作が多すぎます。しばらくしてから再度お試しください");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
