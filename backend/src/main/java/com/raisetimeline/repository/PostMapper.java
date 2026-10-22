package com.raisetimeline.repository;

import com.raisetimeline.entity.Post;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/PostMapper.xml。 */
@Mapper
public interface PostMapper {

  void insert(Post post);

  /** 削除済みは見つからない扱い。 */
  Optional<Post> findById(@Param("id") long id);

  int updateBody(@Param("id") long id, @Param("body") String body);

  int softDelete(@Param("id") long id);

  /** 新しい順。cursorCreatedAt が null なら先頭から。followingOnly なら、viewerId 自身と フォロー中のユーザーの投稿だけ。 */
  List<Post> findTimeline(
      @Param("viewerId") long viewerId,
      @Param("followingOnly") boolean followingOnly,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /** 特定ユーザーの投稿。新しい順。cursorCreatedAt が null なら先頭から。 */
  List<Post> findByUser(
      @Param("userId") long userId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);
}
