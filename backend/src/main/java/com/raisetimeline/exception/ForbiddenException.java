package com.raisetimeline.exception;

public class ForbiddenException extends RuntimeException {

  public ForbiddenException() {
    super("この操作は許可されていません");
  }
}
