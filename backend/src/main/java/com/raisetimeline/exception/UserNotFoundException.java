package com.raisetimeline.exception;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException() {
    super("ユーザーが見つかりません");
  }
}
