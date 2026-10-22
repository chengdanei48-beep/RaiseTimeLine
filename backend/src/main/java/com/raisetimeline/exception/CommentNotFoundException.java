package com.raisetimeline.exception;

public class CommentNotFoundException extends RuntimeException {

  public CommentNotFoundException() {
    super("コメントが見つかりません");
  }
}
