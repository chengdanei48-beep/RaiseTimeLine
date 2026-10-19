package com.raisetimeline.exception;

/** ユーザーIDまたはパスワードの誤り。どちらが誤りかは区別しない。 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("ユーザーIDまたはパスワードが正しくありません");
  }
}
