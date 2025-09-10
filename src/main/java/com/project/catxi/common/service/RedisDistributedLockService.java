package com.project.catxi.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDistributedLockService {
    
    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    
    private static final String LOCK_PREFIX = "fcm:lock:";
    private static final String UNLOCK_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end";
    
    /**
     * 분산락 획득 시도
     * @param lockKey 락 키
     * @param ttlSeconds 락 만료 시간 (초)
     * @return LockResult (성공 여부와 락 식별자)
     */
    public LockResult tryLock(String lockKey, int ttlSeconds) {
        try {
            String fullLockKey = LOCK_PREFIX + lockKey;
            String lockValue = generateLockValue();
            
            Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(fullLockKey, lockValue, Duration.ofSeconds(ttlSeconds));
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("분산락 획득 성공 - Key: {}, Value: {}, TTL: {}s", lockKey, lockValue, ttlSeconds);
                return LockResult.success(lockValue);
            } else {
                log.debug("분산락 획득 실패 - Key: {} (이미 다른 서버에서 처리 중)", lockKey);
                return LockResult.failed();
            }
            
        } catch (Exception e) {
            log.error("분산락 획득 중 오류 - Key: {}, Error: {}", lockKey, e.getMessage(), e);
            return LockResult.failed();
        }
    }
    
    /**
     * 분산락 해제 (원자적 연산)
     * @param lockKey 락 키
     * @param lockValue 락 식별자
     * @return 해제 성공 여부
     */
    public boolean releaseLock(String lockKey, String lockValue) {
        try {
            String fullLockKey = LOCK_PREFIX + lockKey;
            
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(UNLOCK_SCRIPT);
            redisScript.setResultType(Long.class);
            
            Long result = redisTemplate.execute(redisScript, 
                Collections.singletonList(fullLockKey), lockValue);
            
            boolean released = result != null && result.equals(1L);
            
            if (released) {
                log.debug("분산락 해제 성공 - Key: {}, Value: {}", lockKey, lockValue);
            } else {
                log.debug("분산락 해제 실패 - Key: {} (이미 만료되었거나 다른 락)", lockKey);
            }
            
            return released;
            
        } catch (Exception e) {
            log.error("분산락 해제 중 오류 - Key: {}, Error: {}", lockKey, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 락과 함께 작업 실행 (try-with-resources 패턴)
     * @param lockKey 락 키
     * @param ttlSeconds 락 만료 시간
     * @param task 실행할 작업
     * @return 작업 실행 성공 여부
     */
    public boolean executeWithLock(String lockKey, int ttlSeconds, Runnable task) {
        LockResult lockResult = tryLock(lockKey, ttlSeconds);
        
        if (!lockResult.isSuccess()) {
            return false;
        }
        
        try {
            task.run();
            return true;
        } catch (Exception e) {
            log.error("락 보호 작업 실행 중 오류 - Key: {}, Error: {}", lockKey, e.getMessage(), e);
            throw e;
        } finally {
            releaseLock(lockKey, lockResult.getLockValue());
        }
    }
    
    private String generateLockValue() {
        return Thread.currentThread().getName() + ":" + UUID.randomUUID().toString();
    }
    
    /**
     * 락 획득 결과 클래스
     */
    public static class LockResult {
        private final boolean success;
        private final String lockValue;
        
        private LockResult(boolean success, String lockValue) {
            this.success = success;
            this.lockValue = lockValue;
        }
        
        public static LockResult success(String lockValue) {
            return new LockResult(true, lockValue);
        }
        
        public static LockResult failed() {
            return new LockResult(false, null);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getLockValue() {
            return lockValue;
        }
    }
}