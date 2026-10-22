package com.raisetimeline.service;

import com.raisetimeline.dto.ProfileResponse;
import com.raisetimeline.exception.UserNotFoundException;
import com.raisetimeline.repository.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

  private final UserMapper userMapper;

  public UserService(UserMapper userMapper) {
    this.userMapper = userMapper;
  }

  public ProfileResponse profile(long viewerId, long userId) {
    return userMapper
        .findProfile(userId, viewerId)
        .map(p -> ProfileResponse.of(p, viewerId))
        .orElseThrow(UserNotFoundException::new);
  }
}
