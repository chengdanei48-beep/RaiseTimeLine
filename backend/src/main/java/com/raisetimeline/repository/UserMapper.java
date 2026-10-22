package com.raisetimeline.repository;

import com.raisetimeline.entity.User;
import com.raisetimeline.entity.UserProfile;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/UserMapper.xml。 */
@Mapper
public interface UserMapper {

  void insert(User user);

  Optional<User> findByUsername(@Param("username") String username);

  Optional<User> findById(@Param("id") long id);

  /** プロフィール(件数と、viewerId がフォロー済みかつき)。 */
  Optional<UserProfile> findProfile(@Param("id") long id, @Param("viewerId") long viewerId);
}
