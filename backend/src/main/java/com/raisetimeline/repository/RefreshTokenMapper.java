package com.raisetimeline.repository;

import com.raisetimeline.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper {

  @Insert(
      """
      INSERT INTO refresh_tokens (user_id, token_hash, expires_at)
      VALUES (#{userId}, #{tokenHash}, #{expiresAt})
      """)
  void insert(RefreshToken token);

  /** ローテーションの同時実行を直列化するため、行ロックを取って取得する。 */
  @Select("SELECT * FROM refresh_tokens WHERE token_hash = #{tokenHash} FOR UPDATE")
  Optional<RefreshToken> findByHashForUpdate(@Param("tokenHash") String tokenHash);

  @Update("UPDATE refresh_tokens SET revoked_at = #{at} WHERE id = #{id} AND revoked_at IS NULL")
  int revoke(@Param("id") long id, @Param("at") Instant at);

  @Update(
      """
      UPDATE refresh_tokens SET revoked_at = #{at}
      WHERE token_hash = #{tokenHash} AND revoked_at IS NULL
      """)
  int revokeByHash(@Param("tokenHash") String tokenHash, @Param("at") Instant at);

  @Update(
      """
      UPDATE refresh_tokens SET revoked_at = #{at}
      WHERE user_id = #{userId} AND revoked_at IS NULL
      """)
  int revokeAllByUser(@Param("userId") long userId, @Param("at") Instant at);

  @Delete("DELETE FROM refresh_tokens WHERE user_id = #{userId} AND expires_at < #{now}")
  int deleteExpiredByUser(@Param("userId") long userId, @Param("now") Instant now);
}
