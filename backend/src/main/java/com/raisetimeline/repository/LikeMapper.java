package com.raisetimeline.repository;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LikeMapper {

  /** すでにいいね済みなら何もしない(冪等)。 */
  @Insert(
      "INSERT INTO likes (user_id, post_id) VALUES (#{userId}, #{postId}) ON CONFLICT DO NOTHING")
  void insert(@Param("userId") long userId, @Param("postId") long postId);

  @Delete("DELETE FROM likes WHERE user_id = #{userId} AND post_id = #{postId}")
  void delete(@Param("userId") long userId, @Param("postId") long postId);
}
