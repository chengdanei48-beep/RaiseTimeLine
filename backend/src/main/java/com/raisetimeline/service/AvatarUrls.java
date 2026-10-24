package com.raisetimeline.service;

import com.raisetimeline.storage.ObjectStorage;
import org.springframework.stereotype.Component;

/** アイコンの S3 キーから、表示用の署名付き URL を作る。キーが無ければ null(既定のアイコンを表示)。 */
@Component
public class AvatarUrls {

  private final ObjectStorage storage;

  public AvatarUrls(ObjectStorage storage) {
    this.storage = storage;
  }

  /** 署名はローカルで計算するため、一覧の件数分呼んでも S3 とは通信しない。 */
  public String urlOf(String avatarKey) {
    return avatarKey == null || avatarKey.isBlank() ? null : storage.presignGet(avatarKey);
  }
}
