package com.raisetimeline.service;

import com.raisetimeline.storage.ObjectStorage;
import org.springframework.stereotype.Component;

/** 画像(アイコン・投稿画像)の S3 キーから、表示用の署名付き URL を作る。キーが無ければ null(アイコンは既定の表示にする)。 */
@Component
public class ImageUrls {

  private final ObjectStorage storage;

  public ImageUrls(ObjectStorage storage) {
    this.storage = storage;
  }

  /** 署名はローカルで計算するため、一覧の件数分呼んでも S3 とは通信しない。 */
  public String urlOf(String key) {
    return key == null || key.isBlank() ? null : storage.presignGet(key);
  }
}
