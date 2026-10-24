package com.raisetimeline.repository;

import com.raisetimeline.entity.PostCount;
import com.raisetimeline.entity.PostImage;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 投稿カードに載せる付随情報を、投稿ID群に対して 1 クエリずつ一括で取得する(N+1 を避ける)。 SQL は resources/mapper/PostCardMapper.xml。 */
@Mapper
public interface PostCardMapper {

  List<PostImage> findImagesByPostIds(@Param("postIds") List<Long> postIds);

  List<PostCount> countLikes(@Param("postIds") List<Long> postIds);

  List<PostCount> countComments(@Param("postIds") List<Long> postIds);

  List<Long> findLikedPostIds(@Param("userId") long userId, @Param("postIds") List<Long> postIds);
}
