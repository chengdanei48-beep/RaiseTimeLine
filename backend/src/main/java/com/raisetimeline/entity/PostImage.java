package com.raisetimeline.entity;

/** 投稿の画像(S3 のオブジェクトキーと表示順)。 */
public class PostImage {

  private Long postId;
  private String objectKey;
  private int sortOrder;

  public Long getPostId() {
    return postId;
  }

  public void setPostId(Long postId) {
    this.postId = postId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(int sortOrder) {
    this.sortOrder = sortOrder;
  }
}
