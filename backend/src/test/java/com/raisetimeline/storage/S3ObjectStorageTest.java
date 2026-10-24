package com.raisetimeline.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.raisetimeline.config.S3Properties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** 署名付き URL の発行(S3 とは通信しない。署名はローカルで計算する)。 */
class S3ObjectStorageTest {

  private final S3ObjectStorage storage =
      new S3ObjectStorage(
          new S3Properties(
              "test-bucket",
              "ap-northeast-1",
              "http://localhost:9000",
              "test-access",
              "test-secret",
              Duration.ofMinutes(5),
              Duration.ofMinutes(15)));

  @Test
  void 署名付きPUTは有効期限5分で種類とサイズを署名に含める() {
    var upload = storage.presignPut("avatars/1/a.png", "image/png", 1234);

    assertThat(upload.url())
        .startsWith("http://localhost:9000/test-bucket/avatars/1/a.png?")
        .contains("X-Amz-Signature=")
        .contains("X-Amz-Expires=300")
        .contains("content-length")
        .contains("content-type")
        // ブラウザから直接 PUT するため、チェックサムなど余計な必須項目を付けない
        .doesNotContain("checksum");
    assertThat(upload.expiresInSeconds()).isEqualTo(300);
    assertThat(upload.requiredHeaders()).containsEntry("Content-Type", "image/png");
  }

  @Test
  void 署名付きGETは有効期限15分() {
    assertThat(storage.presignGet("avatars/1/a.png"))
        .startsWith("http://localhost:9000/test-bucket/avatars/1/a.png?")
        .contains("X-Amz-Signature=")
        .contains("X-Amz-Expires=900");
  }
}
