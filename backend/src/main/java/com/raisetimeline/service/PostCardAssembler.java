package com.raisetimeline.service;

import com.raisetimeline.dto.PostResponse;
import com.raisetimeline.dto.PostResponse.ImageResponse;
import com.raisetimeline.entity.Post;
import com.raisetimeline.entity.PostCount;
import com.raisetimeline.entity.PostImage;
import com.raisetimeline.repository.PostCardMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 投稿の一覧を、画像・いいね数・コメント数・いいね済みつきの応答にまとめる。 投稿の件数に関わらず、付随情報の取得は種類ごとに 1 クエリ(N+1 を避ける)。
 * 投稿を返す経路(タイムライン・詳細・作成・更新)は必ずここを通す。
 */
@Component
public class PostCardAssembler {

  private final PostCardMapper postCardMapper;
  private final ImageUrls imageUrls;

  public PostCardAssembler(PostCardMapper postCardMapper, ImageUrls imageUrls) {
    this.postCardMapper = postCardMapper;
    this.imageUrls = imageUrls;
  }

  public List<PostResponse> assemble(List<Post> posts, long viewerId) {
    if (posts.isEmpty()) {
      return List.of();
    }
    List<Long> postIds = posts.stream().map(Post::getId).toList();
    Map<Long, List<ImageResponse>> images =
        groupImages(postCardMapper.findImagesByPostIds(postIds));
    Map<Long, Long> likeCounts = toMap(postCardMapper.countLikes(postIds));
    Map<Long, Long> commentCounts = toMap(postCardMapper.countComments(postIds));
    Set<Long> liked = new HashSet<>(postCardMapper.findLikedPostIds(viewerId, postIds));
    return posts.stream()
        .map(
            p ->
                PostResponse.of(
                    p,
                    images.getOrDefault(p.getId(), List.of()),
                    likeCounts.getOrDefault(p.getId(), 0L),
                    commentCounts.getOrDefault(p.getId(), 0L),
                    liked.contains(p.getId()),
                    imageUrls.urlOf(p.getAuthorAvatarKey())))
        .toList();
  }

  public PostResponse assembleOne(Post post, long viewerId) {
    return assemble(List.of(post), viewerId).get(0);
  }

  private static Map<Long, Long> toMap(List<PostCount> counts) {
    Map<Long, Long> map = new HashMap<>();
    counts.forEach(c -> map.put(c.getPostId(), c.getTotal()));
    return map;
  }

  /** 署名はローカルで計算するため、画像の枚数分 URL を作っても S3 とは通信しない。 */
  private Map<Long, List<ImageResponse>> groupImages(List<PostImage> rows) {
    Map<Long, List<ImageResponse>> map = new HashMap<>();
    for (PostImage row : rows) {
      map.computeIfAbsent(row.getPostId(), k -> new ArrayList<>())
          .add(new ImageResponse(imageUrls.urlOf(row.getObjectKey()), row.getSortOrder()));
    }
    return map;
  }
}
