package com.raisetimeline.repository;

import com.raisetimeline.entity.FollowUser;
import com.raisetimeline.entity.User;
import com.raisetimeline.entity.UserProfile;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/UserMapper.xml。 */
@Mapper
public interface UserMapper {

  void insert(User user);

  Optional<User> findByUsername(@Param("username") String username);

  Optional<User> findById(@Param("id") long id);

  /**
   * ユーザー検索(ユーザーID・表示名の部分一致)。pattern は LIKE エスケープ済みで前後に % を付けたもの。 viewerId 自身は除く。cursorKey が null
   * なら先頭から。
   */
  List<FollowUser> searchUsers(
      @Param("viewerId") long viewerId,
      @Param("pattern") String pattern,
      @Param("cursorKey") String cursorKey,
      @Param("cursorId") Long cursorId,
      @Param("limit") int limit);

  /** プロフィール(件数と、viewerId がフォロー済みかつき)。 */
  Optional<UserProfile> findProfile(@Param("id") long id, @Param("viewerId") long viewerId);

  /** プロフィール更新。changeAvatar のときだけ avatarKey を更新する(avatarKey が null ならアイコン削除)。 */
  int updateProfile(
      @Param("id") long id,
      @Param("displayName") String displayName,
      @Param("bio") String bio,
      @Param("changeAvatar") boolean changeAvatar,
      @Param("avatarKey") String avatarKey);
}
