package com.raisetimeline.repository;

import com.raisetimeline.entity.User;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

  @Insert(
      """
      INSERT INTO users (username, password_hash, display_name)
      VALUES (#{username}, #{passwordHash}, #{displayName})
      """)
  @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
  void insert(User user);

  @Select("SELECT * FROM users WHERE lower(username) = lower(#{username})")
  Optional<User> findByUsername(@Param("username") String username);

  @Select("SELECT * FROM users WHERE id = #{id}")
  Optional<User> findById(@Param("id") long id);
}
