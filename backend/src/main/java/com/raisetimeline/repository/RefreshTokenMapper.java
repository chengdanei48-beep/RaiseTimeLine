package com.raisetimeline.repository;

import com.raisetimeline.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** SQL は resources/mapper/RefreshTokenMapper.xml。 */
@Mapper
public interface RefreshTokenMapper {

  void insert(RefreshToken token);

  /** ローテーションの同時実行を直列化するため、行ロックを取って取得する。 */
  Optional<RefreshToken> findByHashForUpdate(@Param("tokenHash") String tokenHash);

  int revoke(@Param("id") long id, @Param("at") Instant at);

  int revokeByHash(@Param("tokenHash") String tokenHash, @Param("at") Instant at);

  int revokeAllByUser(@Param("userId") long userId, @Param("at") Instant at);

  int deleteExpiredByUser(@Param("userId") long userId, @Param("now") Instant now);
}
