package com.raisetimeline.service;

import com.raisetimeline.dto.AuthResponse;
import com.raisetimeline.dto.AuthResult;
import com.raisetimeline.dto.LoginRequest;
import com.raisetimeline.dto.RegisterRequest;
import com.raisetimeline.dto.UserResponse;
import com.raisetimeline.entity.User;
import com.raisetimeline.exception.InvalidCredentialsException;
import com.raisetimeline.exception.UnauthorizedException;
import com.raisetimeline.exception.UsernameAlreadyExistsException;
import com.raisetimeline.repository.UserMapper;
import com.raisetimeline.security.JwtService;
import com.raisetimeline.security.RefreshTokenService;
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
  private final RefreshTokenService refreshTokenService;
  private final AvatarUrls avatarUrls;

  /** 存在しないユーザーでも照合に同程度の時間をかけ、応答時間でIDの有無が分からないようにする。 */
  private final String dummyHash;

  public AuthService(
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      AvatarUrls avatarUrls) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.avatarUrls = avatarUrls;
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public AuthResult register(RegisterRequest request) {
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
    return issueTokens(user);
  }

  @Transactional
  public AuthResult login(LoginRequest request) {
    User user = userMapper.findByUsername(request.username()).orElse(null);
    String hash = user != null ? user.getPasswordHash() : dummyHash;
    boolean matches = passwordEncoder.matches(request.password(), hash);
    if (user == null || !matches) {
      throw new InvalidCredentialsException();
    }
    return issueTokens(user);
  }

  /**
   * リフレッシュトークンをローテーションして、新しいアクセストークンを発行する。
   *
   * <p>失効済みトークンの再提示では全トークンの失効を確定させたうえで 401 にするため、ロールバックしない。
   */
  @Transactional(noRollbackFor = UnauthorizedException.class)
  public AuthResult refresh(String refreshToken) {
    long userId = refreshTokenService.consume(refreshToken);
    User user = userMapper.findById(userId).orElseThrow(UnauthorizedException::new);
    return issueTokens(user);
  }

  @Transactional
  public void logout(String refreshToken) {
    refreshTokenService.revoke(refreshToken);
  }

  public UserResponse me(long userId) {
    return userMapper
        .findById(userId)
        .map(this::userResponse)
        .orElseThrow(UnauthorizedException::new);
  }

  private AuthResult issueTokens(User user) {
    AuthResponse response =
        AuthResponse.bearer(
            jwtService.issue(user.getId()), jwtService.expiresInSeconds(), userResponse(user));
    return new AuthResult(response, refreshTokenService.issue(user.getId()));
  }

  private UserResponse userResponse(User user) {
    return UserResponse.from(user, avatarUrls.urlOf(user.getAvatarKey()));
  }
}
