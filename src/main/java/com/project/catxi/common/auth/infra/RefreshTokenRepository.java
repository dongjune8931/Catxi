package com.project.catxi.common.auth.infra;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository{

  private final RedisTemplate<String, String> tokenRedisTemplate;

  private static final String PREFIX = "refresh:";

  //RefreshToken Redis 저장

  public void save(String key, String token, Duration ttl) {
    tokenRedisTemplate.opsForValue().set(PREFIX + key, token, ttl);
  }

  //RefreshToken 조회
  public Optional<String> findByKey(String key) {
    return Optional.ofNullable(tokenRedisTemplate.opsForValue().get(PREFIX + key));
  }

  //유효성 검증
  public boolean isValid(String key, String token) {
    return findByKey(key).map(saved -> saved.equals(token)).orElse(false);
  }

  // 토큰 삭제
  public void delete(String key) {
    tokenRedisTemplate.delete(PREFIX + key);
  }

}
