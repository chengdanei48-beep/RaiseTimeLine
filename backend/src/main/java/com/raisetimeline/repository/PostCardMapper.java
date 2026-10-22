package com.raisetimeline.repository;

import com.raisetimeline.entity.PostCount;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 投稿カードに載せる付随情報を、投稿ID群に対して 1 クエリずつ一括で取得する(N+1 を避ける)。 いいねとコメントは JOIN すると行が掛け算で膨らむため、別クエリで集計する。 */
@Mapper
public interface PostCardMapper {

  @Select(
      """
      <script>
      SELECT post_id, COUNT(*) AS total
      FROM likes
      WHERE post_id IN
        <foreach collection="postIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      GROUP BY post_id
      </script>
      """)
  List<PostCount> countLikes(@Param("postIds") List<Long> postIds);

  @Select(
      """
      <script>
      SELECT post_id, COUNT(*) AS total
      FROM comments
      WHERE deleted_at IS NULL AND post_id IN
        <foreach collection="postIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      GROUP BY post_id
      </script>
      """)
  List<PostCount> countComments(@Param("postIds") List<Long> postIds);

  @Select(
      """
      <script>
      SELECT post_id
      FROM likes
      WHERE user_id = #{userId} AND post_id IN
        <foreach collection="postIds" item="id" open="(" separator="," close=")">#{id}</foreach>
      </script>
      """)
  List<Long> findLikedPostIds(@Param("userId") long userId, @Param("postIds") List<Long> postIds);
}
