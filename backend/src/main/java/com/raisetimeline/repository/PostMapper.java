package com.raisetimeline.repository;

import com.raisetimeline.entity.Post;
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
public interface PostMapper {

  @Insert("INSERT INTO posts (user_id, body) VALUES (#{userId}, #{body})")
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  void insert(Post post);

  /** 削除済みは見つからない扱い。 */
  @Select(
      """
      SELECT p.id, p.user_id, p.body, p.created_at, p.updated_at,
             u.username AS author_username, u.display_name AS author_display_name
      FROM posts p JOIN users u ON u.id = p.user_id
      WHERE p.id = #{id} AND p.deleted_at IS NULL
      """)
  Optional<Post> findById(@Param("id") long id);

  @Update(
      "UPDATE posts SET body = #{body}, updated_at = now() WHERE id = #{id} AND deleted_at IS NULL")
  int updateBody(@Param("id") long id, @Param("body") String body);

  @Update("UPDATE posts SET deleted_at = now() WHERE id = #{id} AND deleted_at IS NULL")
  int softDelete(@Param("id") long id);

  /** 新しい順。cursorCreatedAt が null なら先頭から。 */
  @Select(
      """
      <script>
      SELECT p.id, p.user_id, p.body, p.created_at, p.updated_at,
             u.username AS author_username, u.display_name AS author_display_name
      FROM posts p JOIN users u ON u.id = p.user_id
      WHERE p.deleted_at IS NULL
      <if test="cursorCreatedAt != null">
        AND (p.created_at, p.id) &lt; (#{cursorCreatedAt}, #{cursorId})
      </if>
      ORDER BY p.created_at DESC, p.id DESC
      LIMIT #{limit}
      </script>
      """)
  List<Post> findTimeline(
      @Param("cursorCreatedAt") Instant cursorCreatedAt,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);
}
