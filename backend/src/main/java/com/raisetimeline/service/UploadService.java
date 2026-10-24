package com.raisetimeline.service;

import com.raisetimeline.dto.PresignRequest;
import com.raisetimeline.dto.PresignRequest.Purpose;
import com.raisetimeline.dto.PresignResponse;
import com.raisetimeline.exception.InvalidRequestException;
import com.raisetimeline.storage.ObjectStorage;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class UploadService {

  /** 投稿画像の最大サイズ(5MB)。 */
  public static final long POST_MAX_BYTES = 5L * 1024 * 1024;

  /** アイコンの最大サイズ(2MB)。 */
  public static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;

  /** 許可する画像の形式と、キーの拡張子。 */
  private static final Map<String, String> EXTENSIONS =
      Map.of(
          "image/jpeg", "jpg",
          "image/png", "png",
          "image/gif", "gif",
          "image/webp", "webp");

  /** キーのファイル名の部分。UUID と拡張子だけを受け付ける(「..」や「/」を含めない)。 */
  private static final Pattern KEY_FILE = Pattern.compile("[A-Za-z0-9-]+\\.[a-z0-9]+");

  private final ObjectStorage storage;

  public UploadService(ObjectStorage storage) {
    this.storage = storage;
  }

  /** 形式・サイズを検証し、サーバーが決めたキーで署名付き PUT URL を発行する(キーはクライアントが決められない)。 */
  public PresignResponse presign(long userId, PresignRequest request) {
    String extension = EXTENSIONS.get(request.contentType());
    if (extension == null) {
      throw new InvalidRequestException("画像は JPEG・PNG・GIF・WebP のいずれかにしてください");
    }
    long max = maxBytes(request.purpose());
    if (request.size() > max) {
      throw new InvalidRequestException("画像は " + (max / 1024 / 1024) + "MB 以下にしてください");
    }
    String key =
        request.purpose().keyPrefix() + "/" + userId + "/" + UUID.randomUUID() + "." + extension;
    var upload = storage.presignPut(key, request.contentType(), request.size());
    return new PresignResponse(
        upload.url(), key, upload.expiresInSeconds(), upload.requiredHeaders());
  }

  /**
   * アップロード済みの画像のキーを確かめる。サーバーが発行した形({purpose}/{ユーザーID}/{uuid}.{拡張子})で、 自分のもので、S3
   * に存在し、サイズ・形式が条件内であること。満たさなければ 400。
   */
  public void verifyUploaded(long userId, Purpose purpose, String key) {
    String prefix = purpose.keyPrefix() + "/" + userId + "/";
    if (key == null
        || !key.startsWith(prefix)
        || !KEY_FILE.matcher(key.substring(prefix.length())).matches()) {
      throw new InvalidRequestException("画像が不正です");
    }
    var stored =
        storage.stat(key).orElseThrow(() -> new InvalidRequestException("画像がアップロードされていません"));
    long max = maxBytes(purpose);
    if (stored.size() > max) {
      throw new InvalidRequestException("画像は " + (max / 1024 / 1024) + "MB 以下にしてください");
    }
    if (!isAllowedContentType(stored.contentType())) {
      throw new InvalidRequestException("画像は JPEG・PNG・GIF・WebP のいずれかにしてください");
    }
  }

  public static long maxBytes(Purpose purpose) {
    return purpose == Purpose.AVATAR ? AVATAR_MAX_BYTES : POST_MAX_BYTES;
  }

  /** 画像として許可する形式か。 */
  public static boolean isAllowedContentType(String contentType) {
    return contentType != null && EXTENSIONS.containsKey(contentType);
  }
}
