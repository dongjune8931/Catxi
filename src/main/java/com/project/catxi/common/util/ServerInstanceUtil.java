package com.project.catxi.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@Slf4j
@Component
public class ServerInstanceUtil {
    
    private String serverInstanceId;
    private boolean isFcmMasterServer = false;
    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    private static final String FCM_MASTER_KEY = "fcm:master:server";
    private static final Duration MASTER_TTL = Duration.ofMinutes(5);
    
    // 마스터 상태 캐싱
    private volatile boolean cachedMasterStatus = false;
    private volatile long lastMasterCheck = 0;
    private static final long MASTER_CHECK_INTERVAL = 30000; // 30초
    
    public ServerInstanceUtil(@Qualifier("chatPubSub") StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
    
    @PostConstruct
    public void init() {
        try {
            // 호스트명 기반으로 서버 ID 생성
            String hostname = InetAddress.getLocalHost().getHostName();
            this.serverInstanceId = hostname;
            log.info("서버 인스턴스 ID 초기화: {}", serverInstanceId);
        } catch (UnknownHostException e) {
            // 호스트명을 얻을 수 없는 경우 현재 시간 기반으로 생성
            this.serverInstanceId = "server-" + (System.currentTimeMillis() % 1000);
            log.warn("호스트명을 얻을 수 없어 임시 ID 생성: {}", serverInstanceId);
        }
        
        // FCM 마스터 서버 등록 시도
        tryRegisterAsFcmMaster();
        
        // 초기 캐시 설정
        this.cachedMasterStatus = this.isFcmMasterServer;
        this.lastMasterCheck = System.currentTimeMillis();
    }
    
    /**
     * FCM 마스터 서버 등록 시도
     */
    private void tryRegisterAsFcmMaster() {
        try {
            // Redis SET NX (존재하지 않을 때만 설정)
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                FCM_MASTER_KEY, 
                serverInstanceId, 
                MASTER_TTL
            );
            
            if (Boolean.TRUE.equals(success)) {
                this.isFcmMasterServer = true;
                this.cachedMasterStatus = true; // 캐시도 업데이트
                log.info("FCM 마스터 서버로 등록 성공: {}", serverInstanceId);
                
                // 주기적으로 TTL 갱신하는 스케줄러 시작
                startMasterHeartbeat();
            } else {
                String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
                log.info("다른 서버가 FCM 마스터로 등록됨: {}, 현재 서버: {}", currentMaster, serverInstanceId);
            }
        } catch (Exception e) {
            log.error("FCM 마스터 서버 등록 실패", e);
        }
    }
    
    /**
     * 마스터 서버 하트비트 (TTL 갱신)
     */
    private void startMasterHeartbeat() {
        Thread heartbeatThread = new Thread(() -> {
            while (isFcmMasterServer) {
                try {
                    Thread.sleep(Duration.ofMinutes(2).toMillis()); // 2분마다 갱신
                    
                    // 현재 마스터가 자신인지 확인하고 TTL 갱신
                    String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
                    if (serverInstanceId.equals(currentMaster)) {
                        redisTemplate.expire(FCM_MASTER_KEY, MASTER_TTL);
                        log.debug("FCM 마스터 TTL 갱신: {}", serverInstanceId);
                    } else {
                        // 다른 서버가 마스터가 됨
                        isFcmMasterServer = false;
                        cachedMasterStatus = false; // 캐시도 업데이트
                        log.info("FCM 마스터 권한 상실: {} -> {}", serverInstanceId, currentMaster);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("FCM 마스터 하트비트 오류", e);
                }
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.setName("fcm-master-heartbeat");
        heartbeatThread.start();
    }
    
    /**
     * 현재 서버가 FCM 처리를 담당하는지 확인 (캐시 최적화)
     */
    public boolean shouldProcessFcm() {
        long now = System.currentTimeMillis();
        
        // 30초마다만 Redis 확인
        if (now - lastMasterCheck > MASTER_CHECK_INTERVAL) {
            refreshMasterStatus();
            lastMasterCheck = now;
        }
        
        return cachedMasterStatus;
    }
    
    /**
     * 마스터 상태 새로고침
     */
    private void refreshMasterStatus() {
        if (!isFcmMasterServer) {
            // 마스터가 아니면 혹시 마스터가 사라졌는지 확인
            String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
            if (currentMaster == null) {
                log.info("FCM 마스터 서버가 없음, 재등록 시도");
                tryRegisterAsFcmMaster();
            }
        }
        
        // 캐시 업데이트
        this.cachedMasterStatus = this.isFcmMasterServer;
        log.debug("마스터 상태 캐시 갱신: ServerId={}, IsMaster={}", 
            serverInstanceId, cachedMasterStatus);
    }
    
    /**
     * 현재 서버 인스턴스 ID 반환
     */
    public String getServerInstanceId() {
        return serverInstanceId;
    }
}