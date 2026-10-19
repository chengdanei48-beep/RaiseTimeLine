package com.raisetimeline.exception;

public class UsernameAlreadyExistsException extends RuntimeException {

  public UsernameAlreadyExistsException() {
    super("このユーザーIDは既に使われています");
  }
}
