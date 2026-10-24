package com.raisetimeline.storage;

import java.util.Map;
import java.util.Optional;

/** 画像の保存先(S3)。画像本体は API サーバーを通さず、ブラウザが署名付き URL で直接やり取りする。 */
public interface ObjectStorage {

  /** アップロード用の署名付き URL と、アップロード時に必ず付けるヘッダー。 */
  record PresignedUpload(String url, long expiresInSeconds, Map<String, String> requiredHeaders) {

    public PresignedUpload {
      requiredHeaders = Map.copyOf(requiredHeaders);
    }
  }

  /** S3 上のオブジェクトの情報。 */
  record StoredObject(long size, String contentType) {}

  /** 署名付き PUT URL を発行する。contentType と size は署名に含め、別の内容ではアップロードできないようにする。 */
  PresignedUpload presignPut(String key, String contentType, long size);

  /** 表示用の署名付き GET URL を発行する(署名はローカルで計算し、S3 とは通信しない)。 */
  String presignGet(String key);

  /** オブジェクトの情報を取得する(HEAD)。存在しなければ空。 */
  Optional<StoredObject> stat(String key);
}
