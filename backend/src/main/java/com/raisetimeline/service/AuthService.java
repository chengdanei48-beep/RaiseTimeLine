package com.raisetimeline.service;

import com.raisetimeline.dto.AuthResponse;
import com.raisetimeline.dto.LoginRequest;
import com.raisetimeline.dto.RegisterRequest;
import com.raisetimeline.dto.UserResponse;
import com.raisetimeline.entity.User;
import com.raisetimeline.exception.InvalidCredentialsException;
import com.raisetimeline.exception.UnauthorizedException;
import com.raisetimeline.exception.UsernameAlreadyExistsException;
import com.raisetimeline.repository.UserMapper;
import com.raisetimeline.security.JwtService;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  /** 存在しないユーザーでも照合に同程度の時間をかけ、応答時間でIDの有無が分からないようにする。 */
  private final String dummyHash;

  public AuthService(
      UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    User user = new User();
    user.setUsername(request.username());
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setDisplayName(request.displayName());
    try {
      userMapper.insert(user);
    } catch (DuplicateKeyException e) {
      // lower(username) の一意制約。同時登録の競合もここに来る
      throw new UsernameAlreadyExistsException();
    }
    return issueToken(user);
  }

  public AuthResponse login(LoginRequest request) {
    User user = userMapper.findByUsername(request.username()).orElse(null);
    String hash = user != null ? user.getPasswordHash() : dummyHash;
    boolean matches = passwordEncoder.matches(request.password(), hash);
    if (user == null || !matches) {
      throw new InvalidCredentialsException();
    }
    return issueToken(user);
  }

  public UserResponse me(long userId) {
    return userMapper
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(UnauthorizedException::new);
  }

  private AuthResponse issueToken(User user) {
    return AuthResponse.bearer(
        jwtService.issue(user.getId()), jwtService.expiresInSeconds(), UserResponse.from(user));
  }
}
