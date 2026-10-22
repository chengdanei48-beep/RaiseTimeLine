package com.raisetimeline.repository;

import com.raisetimeline.entity.FollowUser;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/FollowMapper.xml。 */
@Mapper
public interface FollowMapper {

  /** すでにフォロー済みなら何もしない(冪等)。 */
  void insert(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

  void delete(@Param("followerId") long followerId, @Param("followeeId") long followeeId);

  /** userId がフォローしている人。フォローした新しい順。cursorFollowedAt が null なら先頭から。 */
  List<FollowUser> findFollowing(
      @Param("userId") long userId,
      @Param("cursorFollowedAt") Instant cursorFollowedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /** userId をフォローしている人。フォローされた新しい順。cursorFollowedAt が null なら先頭から。 */
  List<FollowUser> findFollowers(
      @Param("userId") long userId,
      @Param("cursorFollowedAt") Instant cursorFollowedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /** userIds のうち、viewerId がフォロー済みのユーザーID(一覧の followedByMe を一括で調べる)。 */
  List<Long> findFollowedIds(
      @Param("viewerId") long viewerId, @Param("userIds") List<Long> userIds);
}
