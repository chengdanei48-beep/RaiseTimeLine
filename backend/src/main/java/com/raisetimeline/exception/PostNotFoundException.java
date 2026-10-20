package com.raisetimeline.exception;

public class PostNotFoundException extends RuntimeException {

  public PostNotFoundException() {
    super("投稿が見つかりません");
  }
}
