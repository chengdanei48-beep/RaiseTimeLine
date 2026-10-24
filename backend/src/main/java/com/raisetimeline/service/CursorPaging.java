package com.raisetimeline.service;

import com.raisetimeline.exception.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** カーソル方式ページングの共通処理(limit の検証、カーソルの符号化・復号)。 */
final class CursorPaging {

  static final int DEFAULT_LIMIT = 20;
  static final int MAX_LIMIT = 50;

  private static final String INVALID_CURSOR = "cursor が不正です";

  /** 最後に返した項目の位置。 */
  record Position(Instant createdAt, long id) {}

  private CursorPaging() {}

  static int pageSize(Integer limit) {
    int size = limit == null ? DEFAULT_LIMIT : limit;
    if (size < 1 || size > MAX_LIMIT) {
      throw new InvalidRequestException("limit は1〜" + MAX_LIMIT + "で指定してください");
    }
    return size;
  }

  /** cursor が未指定なら null。 */
  static Position decode(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      String[] parts = raw.split("_", 2);
      if (parts.length != 2) {
        throw new InvalidRequestException(INVALID_CURSOR);
      }
      return new Position(Instant.parse(parts[0]), Long.parseLong(parts[1]));
    } catch (RuntimeException e) {
      throw new InvalidRequestException(INVALID_CURSOR);
    }
  }

  /** 文字列キー(並び順の値)と ID で位置を表すカーソル。キーに `:` を含まないこと(ユーザーIDは含まない)。 */
  record KeyPosition(String key, long id) {}

  /** cursor が未指定なら null。 */
  static KeyPosition decodeKey(String cursor) {
    if (cursor == null || cursor.isEmpty()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int i = raw.lastIndexOf(':');
      if (i < 1) {
        throw new InvalidRequestException(INVALID_CURSOR);
      }
      return new KeyPosition(raw.substring(0, i), Long.parseLong(raw.substring(i + 1)));
    } catch (RuntimeException e) {
      throw new InvalidRequestException(INVALID_CURSOR);
    }
  }

  /** 不透明な文字列。中身は「キー:ID」。 */
  static String encodeKey(String key, long id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((key + ":" + id).getBytes(StandardCharsets.UTF_8));
  }

  /** 不透明な文字列。中身は「作成日時_ID」。 */
  static String encode(Instant createdAt, long id) {
    String raw = createdAt + "_" + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
