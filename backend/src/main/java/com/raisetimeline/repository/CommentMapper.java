package com.raisetimeline.repository;

import com.raisetimeline.entity.Comment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CommentMapper {

  @Insert("INSERT INTO comments (post_id, user_id, body) VALUES (#{postId}, #{userId}, #{body})")
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  void insert(Comment comment);

  /** 削除済みのコメント、削除済みの投稿のコメントは見つからない扱い。 */
  @Select(
      """
      SELECT c.id, c.post_id, c.user_id, c.body, c.created_at,
             u.username AS author_username, u.display_name AS author_display_name
      FROM comments c
        JOIN users u ON u.id = c.user_id
        JOIN posts p ON p.id = c.post_id AND p.deleted_at IS NULL
      WHERE c.id = #{id} AND c.deleted_at IS NULL
      """)
  Optional<Comment> findById(@Param("id") long id);

  @Update("UPDATE comments SET deleted_at = now() WHERE id = #{id} AND deleted_at IS NULL")
  int softDelete(@Param("id") long id);

  /** 古い順。cursorCreatedAt が null なら先頭から。 */
  @Select(
      """
      <script>
      SELECT c.id, c.post_id, c.user_id, c.body, c.created_at,
             u.username AS author_username, u.display_name AS author_display_name
      FROM comments c JOIN users u ON u.id = c.user_id
      WHERE c.post_id = #{postId} AND c.deleted_at IS NULL
      <if test="cursorCreatedAt != null">
        AND (c.created_at, c.id) &gt; (#{cursorCreatedAt}, #{cursorId})
      </if>
      ORDER BY c.created_at ASC, c.id ASC
      LIMIT #{limit}
      </script>
      """)
  List<Comment> findByPost(
      @Param("postId") long postId,
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);
}
