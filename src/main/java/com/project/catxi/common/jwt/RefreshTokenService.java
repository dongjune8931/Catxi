package com.project.catxi.common.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Qualifier("tokenRedisTemplate")
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String USER_TOKEN_MAPPING_PREFIX = "user_token:";

    public void saveRefreshToken(String email, String refreshToken, long ttlSeconds) {
        try {
            String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
            String userKey = USER_TOKEN_MAPPING_PREFIX + email;
            
            // 기존 토큰이 있다면 삭제
            String existingToken = redisTemplate.opsForValue().get(userKey);
            if (existingToken != null) {
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + existingToken);
            }
            
            // 새 토큰 저장
            redisTemplate.opsForValue().set(tokenKey, email, Duration.ofSeconds(ttlSeconds));
            redisTemplate.opsForValue().set(userKey, refreshToken, Duration.ofSeconds(ttlSeconds));
            
            log.info("✅ 리프레시 토큰 저장 완료: email={}, ttl={}초", email, ttlSeconds);
        } catch (Exception e) {
            log.error("❌ 리프레시 토큰 저장 실패: email={}", email, e);
            throw new RuntimeException("리프레시 토큰 저장 중 오류 발생", e);
        }
    }

    public Optional<String> getEmailByRefreshToken(String refreshToken) {
        try {
            String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
            String email = redisTemplate.opsForValue().get(tokenKey);
            return Optional.ofNullable(email);
        } catch (Exception e) {
            log.error("❌ 리프레시 토큰 조회 실패: token={}", refreshToken, e);
            return Optional.empty();
        }
    }

    public boolean isValidRefreshToken(String refreshToken) {
        try {
            String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
            return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey));
        } catch (Exception e) {
            log.error("❌ 리프레시 토큰 유효성 검사 실패: token={}", refreshToken, e);
            return false;
        }
    }

    public void deleteRefreshToken(String refreshToken) {
        try {
            String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
            
            // 이메일 조회 후 사용자 매핑도 삭제
            String email = redisTemplate.opsForValue().get(tokenKey);
            if (email != null) {
                String userKey = USER_TOKEN_MAPPING_PREFIX + email;
                redisTemplate.delete(userKey);
            }
            
            redisTemplate.delete(tokenKey);
            log.info("✅ 리프레시 토큰 삭제 완료: token={}", refreshToken);
        } catch (Exception e) {
            log.error("❌ 리프레시 토큰 삭제 실패: token={}", refreshToken, e);
        }
    }

    public void deleteAllRefreshTokensByEmail(String email) {
        try {
            String userKey = USER_TOKEN_MAPPING_PREFIX + email;
            String refreshToken = redisTemplate.opsForValue().get(userKey);
            
            if (refreshToken != null) {
                String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
                redisTemplate.delete(tokenKey);
                redisTemplate.delete(userKey);
                log.info("✅ 사용자의 모든 리프레시 토큰 삭제 완료: email={}", email);
            }
        } catch (Exception e) {
            log.error("❌ 사용자 리프레시 토큰 삭제 실패: email={}", email, e);
        }
    }

    public boolean hasRefreshToken(String email) {
        try {
            String userKey = USER_TOKEN_MAPPING_PREFIX + email;
            return Boolean.TRUE.equals(redisTemplate.hasKey(userKey));
        } catch (Exception e) {
            log.error("❌ 리프레시 토큰 존재 여부 확인 실패: email={}", email, e);
            return false;
        }
    }

    public long getRefreshTokenTtl(String refreshToken) {
        try {
            String tokenKey = REFRESH_TOKEN_PREFIX + refreshToken;
            Long ttl = redisTemplate.getExpire(tokenKey);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.error("❌ 리프레시 토큰 TTL 조회 실패: token={}", refreshToken, e);
            return -1;
        }
    }
}