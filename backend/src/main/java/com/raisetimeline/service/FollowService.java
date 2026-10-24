package com.raisetimeline.service;

import com.raisetimeline.dto.FollowUserListResponse;
import com.raisetimeline.entity.FollowUser;
import com.raisetimeline.exception.InvalidRequestException;
import com.raisetimeline.exception.UserNotFoundException;
import com.raisetimeline.repository.FollowMapper;
import com.raisetimeline.repository.UserMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FollowService {

  private final FollowMapper followMapper;
  private final UserMapper userMapper;
  private final UserListAssembler userListAssembler;

  public FollowService(
      FollowMapper followMapper, UserMapper userMapper, UserListAssembler userListAssembler) {
    this.followMapper = followMapper;
    this.userMapper = userMapper;
    this.userListAssembler = userListAssembler;
  }

  /** 冪等。すでにフォロー済みでも何もしない。 */
  @Transactional
  public void follow(long followerId, long followeeId) {
    if (followerId == followeeId) {
      throw new InvalidRequestException("自分自身はフォローできません");
    }
    requireUser(followeeId);
    followMapper.insert(followerId, followeeId);
  }

  /** 冪等。フォローしていなくても何もしない。 */
  @Transactional
  public void unfollow(long followerId, long followeeId) {
    requireUser(followeeId);
    followMapper.delete(followerId, followeeId);
  }

  public FollowUserListResponse following(
      long viewerId, long userId, String cursor, Integer limit) {
    requireUser(userId);
    int size = CursorPaging.pageSize(limit);
    CursorPaging.Position position = CursorPaging.decode(cursor);
    // 次ページの有無を判定するため 1 件多く取る
    List<FollowUser> rows =
        followMapper.findFollowing(
            userId,
            position == null ? null : position.createdAt(),
            position == null ? null : position.id(),
            size + 1);
    return toResponse(viewerId, rows, size);
  }

  public FollowUserListResponse followers(
      long viewerId, long userId, String cursor, Integer limit) {
    requireUser(userId);
    int size = CursorPaging.pageSize(limit);
    CursorPaging.Position position = CursorPaging.decode(cursor);
    List<FollowUser> rows =
        followMapper.findFollowers(
            userId,
            position == null ? null : position.createdAt(),
            position == null ? null : position.id(),
            size + 1);
    return toResponse(viewerId, rows, size);
  }

  private FollowUserListResponse toResponse(long viewerId, List<FollowUser> rows, int size) {
    boolean hasNext = rows.size() > size;
    List<FollowUser> page = hasNext ? rows.subList(0, size) : rows;
    String nextCursor = null;
    if (hasNext) {
      FollowUser last = page.get(page.size() - 1);
      nextCursor = CursorPaging.encode(last.getFollowedAt(), last.getId());
    }
    return new FollowUserListResponse(userListAssembler.assemble(viewerId, page), nextCursor);
  }

  private void requireUser(long userId) {
    if (!userMapper.existsById(userId)) {
      throw new UserNotFoundException();
    }
  }
}
