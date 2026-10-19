package com.raisetimeline.exception;

public class UnauthorizedException extends RuntimeException {

  public UnauthorizedException() {
    super("ログインが必要です");
  }
}
