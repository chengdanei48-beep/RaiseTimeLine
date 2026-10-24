package com.raisetimeline.service;

import com.raisetimeline.dto.FollowUserResponse;
import com.raisetimeline.entity.FollowUser;
import com.raisetimeline.repository.FollowMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ユーザーの一覧(フォロー中・フォロワー・検索結果)を、followedByMe つきの応答にまとめる。 followedByMe は、一覧の ID 群に対して 1 クエリで一括取得する(N+1
 * を避ける)。
 */
@Component
public class UserListAssembler {

  private final FollowMapper followMapper;
  private final AvatarUrls avatarUrls;

  public UserListAssembler(FollowMapper followMapper, AvatarUrls avatarUrls) {
    this.followMapper = followMapper;
    this.avatarUrls = avatarUrls;
  }

  /** 自分自身の行の followedByMe は null(フォローボタンを出さない)。 */
  public List<FollowUserResponse> assemble(long viewerId, List<FollowUser> users) {
    Set<Long> followed = new HashSet<>();
    if (!users.isEmpty()) {
      followed.addAll(
          followMapper.findFollowedIds(viewerId, users.stream().map(FollowUser::getId).toList()));
    }
    return users.stream()
        .map(
            u ->
                new FollowUserResponse(
                    u.getId(),
                    u.getUsername(),
                    u.getDisplayName(),
                    avatarUrls.urlOf(u.getAvatarKey()),
                    u.getBio(),
                    u.getId() == viewerId ? null : followed.contains(u.getId())))
        .toList();
  }
}
