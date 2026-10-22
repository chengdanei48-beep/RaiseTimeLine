package com.raisetimeline.repository;

import com.raisetimeline.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/CommentMapper.xml。 */
@Mapper
public interface CommentMapper {

  void insert(Comment comment);

  /** 削除済みのコメント、削除済みの投稿のコメントは見つからない扱い。 */
  Optional<Comment> findById(@Param("id") long id);

  int softDelete(@Param("id") long id);

  /** 古い順。cursorCreatedAt が null なら先頭から。 */
  List<Comment> findByPost(
      @Param("postId") long postId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);
}
