package com.raisetimeline.repository;

import com.raisetimeline.entity.User;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/UserMapper.xml。 */
@Mapper
public interface UserMapper {

  void insert(User user);

  Optional<User> findByUsername(@Param("username") String username);

  Optional<User> findById(@Param("id") long id);
}
