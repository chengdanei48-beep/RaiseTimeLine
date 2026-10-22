package com.raisetimeline.entity;

/** 投稿ごとの件数(いいね数・コメント数の集計結果)。 */
public class PostCount {

  private Long postId;
  private Long total;

  public Long getPostId() {
    return postId;
  }

  public void setPostId(Long postId) {
    this.postId = postId;
  }

  public Long getTotal() {
    return total;
  }

  public void setTotal(Long total) {
    this.total = total;
  }
}
