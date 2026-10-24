package com.raisetimeline.service;

import com.raisetimeline.dto.PostResponse;
import com.raisetimeline.entity.Post;
import com.raisetimeline.entity.PostCount;
import com.raisetimeline.repository.PostCardMapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 投稿の一覧を、いいね数・コメント数・いいね済みつきの応答にまとめる。 投稿の件数に関わらず、付随情報の取得は種類ごとに 1 クエリ(N+1 を避ける)。
 * 投稿を返す経路(タイムライン・詳細・作成・更新)は必ずここを通す。
 */
@Component
public class PostCardAssembler {

  private final PostCardMapper postCardMapper;
  private final AvatarUrls avatarUrls;

  public PostCardAssembler(PostCardMapper postCardMapper, AvatarUrls avatarUrls) {
    this.postCardMapper = postCardMapper;
    this.avatarUrls = avatarUrls;
  }

  public List<PostResponse> assemble(List<Post> posts, long viewerId) {
    if (posts.isEmpty()) {
      return List.of();
    }
    List<Long> postIds = posts.stream().map(Post::getId).toList();
    Map<Long, Long> likeCounts = toMap(postCardMapper.countLikes(postIds));
    Map<Long, Long> commentCounts = toMap(postCardMapper.countComments(postIds));
    Set<Long> liked = new HashSet<>(postCardMapper.findLikedPostIds(viewerId, postIds));
    return posts.stream()
        .map(
            p ->
                PostResponse.of(
                    p,
                    likeCounts.getOrDefault(p.getId(), 0L),
                    commentCounts.getOrDefault(p.getId(), 0L),
                    liked.contains(p.getId()),
                    avatarUrls.urlOf(p.getAuthorAvatarKey())))
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
}
