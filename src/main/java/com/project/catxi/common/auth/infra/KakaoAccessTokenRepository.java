package com.project.catxi.common.auth.infra;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KakaoAccessTokenRepository {

  private final RedisTemplate<String, String> tokenRedisTemplate;

  public KakaoAccessTokenRepository(@Qualifier("tokenRedisTemplate") RedisTemplate<String, String> tokenRedisTemplate) {
    this.tokenRedisTemplate = tokenRedisTemplate;
  }

  private static final String PREFIX = "kakao_access:";

  //6시간 TTL(자동)
  public void save(String email, String accessToken, Duration ttl) {
    tokenRedisTemplate.opsForValue().set(PREFIX + email, accessToken, ttl);
  }

  public Optional<String> findByEmail(String email) {
    return Optional.ofNullable(tokenRedisTemplate.opsForValue().get(PREFIX + email));
  }

  public void delete(String email) {
    tokenRedisTemplate.delete(PREFIX + email);
  }

  public boolean exists(String email) {
    return tokenRedisTemplate.hasKey(PREFIX + email);
  }
}