package com.raisetimeline.service;

import com.raisetimeline.dto.FollowUserListResponse;
import com.raisetimeline.dto.ProfileResponse;
import com.raisetimeline.dto.UpdateProfileRequest;
import com.raisetimeline.entity.FollowUser;
import com.raisetimeline.exception.InvalidRequestException;
import com.raisetimeline.exception.UserNotFoundException;
import com.raisetimeline.repository.UserMapper;
import com.raisetimeline.storage.ObjectStorage;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

  public static final int KEYWORD_MAX_LENGTH = 50;

  /** アイコンのキーの形。サーバーが発行した avatars/{ユーザーID}/{uuid}.{拡張子} だけを受け付ける。 */
  private static final Pattern AVATAR_FILE = Pattern.compile("[A-Za-z0-9-]+\\.[a-z0-9]+");

  private final UserMapper userMapper;
  private final UserListAssembler userListAssembler;
  private final AvatarUrls avatarUrls;
  private final ObjectStorage storage;

  public UserService(
      UserMapper userMapper,
      UserListAssembler userListAssembler,
      AvatarUrls avatarUrls,
      ObjectStorage storage) {
    this.userMapper = userMapper;
    this.userListAssembler = userListAssembler;
    this.avatarUrls = avatarUrls;
    this.storage = storage;
  }

  public ProfileResponse profile(long viewerId, long userId) {
    return userMapper
        .findProfile(userId, viewerId)
        .map(p -> ProfileResponse.of(p, viewerId, avatarUrls.urlOf(p.getAvatarKey())))
        .orElseThrow(UserNotFoundException::new);
  }

  /** 自分のプロフィールを更新する。ユーザーID(username)は変更できない。 */
  @Transactional
  public ProfileResponse updateProfile(long userId, UpdateProfileRequest request) {
    boolean changeAvatar = request.isAvatarKeySpecified();
    String avatarKey = request.getAvatarKey();
    if (changeAvatar && avatarKey != null) {
      validateAvatarKey(userId, avatarKey);
    }
    String bio = request.getBio() == null || request.getBio().isEmpty() ? null : request.getBio();
    userMapper.updateProfile(userId, request.getDisplayName(), bio, changeAvatar, avatarKey);
    return profile(userId, userId);
  }

  /** 自分が発行したキーで、S3 に存在し、2MB 以下の画像であることを確かめる。 */
  private void validateAvatarKey(long userId, String key) {
    String prefix = "avatars/" + userId + "/";
    if (!key.startsWith(prefix) || !AVATAR_FILE.matcher(key.substring(prefix.length())).matches()) {
      throw new InvalidRequestException("アイコン画像が不正です");
    }
    var stored =
        storage.stat(key).orElseThrow(() -> new InvalidRequestException("アイコン画像がアップロードされていません"));
    if (stored.size() > UploadService.AVATAR_MAX_BYTES) {
      throw new InvalidRequestException("アイコン画像は 2MB 以下にしてください");
    }
    if (!UploadService.isAllowedContentType(stored.contentType())) {
      throw new InvalidRequestException("アイコン画像は JPEG・PNG・GIF・WebP のいずれかにしてください");
    }
  }

  /** ユーザーID・表示名の部分一致検索(大文字小文字を区別しない)。自分は除く。 */
  public FollowUserListResponse search(long viewerId, String q, String cursor, Integer limit) {
    String keyword = normalizeKeyword(q);
    int size = CursorPaging.pageSize(limit);
    CursorPaging.KeyPosition position = CursorPaging.decodeKey(cursor);
    // 次ページの有無を判定するため 1 件多く取る
    List<FollowUser> rows =
        userMapper.searchUsers(
            viewerId,
            "%" + escapeLike(keyword) + "%",
            position == null ? null : position.key(),
            position == null ? null : position.id(),
            size + 1);
    boolean hasNext = rows.size() > size;
    List<FollowUser> page = hasNext ? rows.subList(0, size) : rows;
    String nextCursor = null;
    if (hasNext) {
      FollowUser last = page.get(page.size() - 1);
      nextCursor =
          CursorPaging.encodeKey(last.getUsername().toLowerCase(Locale.ROOT), last.getId());
    }
    return new FollowUserListResponse(userListAssembler.assemble(viewerId, page), nextCursor);
  }

  /** 前後の空白と先頭の `@` を取り除き、1〜50文字であることを確かめる。 */
  private static String normalizeKeyword(String q) {
    String keyword = q == null ? "" : q.strip();
    while (keyword.startsWith("@")) {
      keyword = keyword.substring(1).strip();
    }
    if (keyword.isEmpty()) {
      throw new InvalidRequestException("検索するユーザーIDまたは表示名を入力してください");
    }
    if (keyword.length() > KEYWORD_MAX_LENGTH) {
      throw new InvalidRequestException("検索語は" + KEYWORD_MAX_LENGTH + "文字以内で入力してください");
    }
    return keyword;
  }

  /** LIKE の特別な文字(`\` `%` `_`)を、文字そのものとして扱うようにエスケープする。 */
  private static String escapeLike(String keyword) {
    return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
