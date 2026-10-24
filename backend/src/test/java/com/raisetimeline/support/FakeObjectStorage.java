package com.raisetimeline.support;

import com.raisetimeline.storage.ObjectStorage;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** テスト用の保存先。S3 に接続せず、アップロード済みのオブジェクトをメモリに持つ。 */
public class FakeObjectStorage implements ObjectStorage {

  private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

  /** アップロードが済んだ状態にする。 */
  public void put(String key, long size, String contentType) {
    objects.put(key, new StoredObject(size, contentType));
  }

  public void clear() {
    objects.clear();
  }

  @Override
  public PresignedUpload presignPut(String key, String contentType, long size) {
    return new PresignedUpload(
        "https://fake-s3.test/put/" + key, 300, Map.of("Content-Type", contentType));
  }

  @Override
  public String presignGet(String key) {
    return "https://fake-s3.test/get/" + key;
  }

  @Override
  public Optional<StoredObject> stat(String key) {
    return Optional.ofNullable(objects.get(key));
  }
}
