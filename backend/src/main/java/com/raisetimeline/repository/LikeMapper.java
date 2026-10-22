package com.raisetimeline.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/LikeMapper.xml。 */
@Mapper
public interface LikeMapper {

  /** すでにいいね済みなら何もしない(冪等)。 */
  void insert(@Param("userId") long userId, @Param("postId") long postId);

  void delete(@Param("userId") long userId, @Param("postId") long postId);
}
