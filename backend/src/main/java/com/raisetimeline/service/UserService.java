package com.raisetimeline.service;

import com.raisetimeline.dto.FollowUserListResponse;
import com.raisetimeline.dto.ProfileResponse;
import com.raisetimeline.entity.FollowUser;
import com.raisetimeline.exception.InvalidRequestException;
import com.raisetimeline.exception.UserNotFoundException;
import com.raisetimeline.repository.UserMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

  public static final int KEYWORD_MAX_LENGTH = 50;

  private final UserMapper userMapper;
  private final UserListAssembler userListAssembler;

  public UserService(UserMapper userMapper, UserListAssembler userListAssembler) {
    this.userMapper = userMapper;
    this.userListAssembler = userListAssembler;
  }

  public ProfileResponse profile(long viewerId, long userId) {
    return userMapper
        .findProfile(userId, viewerId)
        .map(p -> ProfileResponse.of(p, viewerId))
        .orElseThrow(UserNotFoundException::new);
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
