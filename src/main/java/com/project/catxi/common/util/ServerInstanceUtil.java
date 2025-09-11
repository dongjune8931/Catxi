package com.project.catxi.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@Slf4j
@Component
public class ServerInstanceUtil {
    
    private String serverInstanceId;
    private boolean isFcmMasterServer = false;
    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;
    private static final String FCM_MASTER_KEY = "fcm:master:server";
    private static final Duration MASTER_TTL = Duration.ofMinutes(5);
    
    // 마스터 상태 캐싱
    private volatile boolean cachedMasterStatus = false;
    private volatile long lastMasterCheck = 0;
    private static final long MASTER_CHECK_INTERVAL = 30000; // 30초
    
    // FCM 리스너 컨테이너 및 구독 상태 관리
    private RedisMessageListenerContainer fcmListenerContainer;
    private boolean isFcmChannelSubscribed = false;
    private boolean applicationReady = false;
    
    public ServerInstanceUtil(@Qualifier("chatPubSub") StringRedisTemplate redisTemplate, 
                             ApplicationContext applicationContext) {
        this.redisTemplate = redisTemplate;
        this.applicationContext = applicationContext;
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
        
        // FCM 마스터 서버 등록 시도 (리스너 컨테이너 지연 로딩)
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

                // ApplicationReady 이후에만 FCM 채널 구독 시도
                if (applicationReady) {
                    subscribeFcmChannels();
                }
                
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
                        
                        // FCM 채널 구독 해제
                        unsubscribeFcmChannels();
                        
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
                
                // 마스터 재등록 시도
                Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    FCM_MASTER_KEY, 
                    serverInstanceId, 
                    MASTER_TTL
                );
                
                if (Boolean.TRUE.equals(success)) {
                    this.isFcmMasterServer = true;
                    this.cachedMasterStatus = true;
                    log.info("FCM 마스터 서버로 재등록 성공: {}", serverInstanceId);
                    
                    // ApplicationReady 이후에만 FCM 채널 구독 시도
                    if (applicationReady) {
                        subscribeFcmChannels();
                    }
                    
                    // 하트비트 시작
                    startMasterHeartbeat();
                }
            }
        }
        
        // 캐시 업데이트
        this.cachedMasterStatus = this.isFcmMasterServer;
        log.debug("마스터 상태 캐시 갱신: ServerId={}, IsMaster={}", 
            serverInstanceId, cachedMasterStatus);
    }
    
    /**
     * 애플리케이션 준비 완료 시 FCM 채널 구독 처리
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        this.applicationReady = true;
        
        // 마스터 서버인 경우 FCM 채널 구독 시작
        if (isFcmMasterServer && !isFcmChannelSubscribed) {
            subscribeFcmChannels();
        }
    }
    
    /**
     * 현재 서버 인스턴스 ID 반환
     */
    public String getServerInstanceId() {
        return serverInstanceId;
    }
    
    /**
     * FCM 채널 구독 시작
     */
    private void subscribeFcmChannels() {
        if (!isFcmChannelSubscribed) {
            try {
                // FCM 리스너 컨테이너 지연 로딩
                if (fcmListenerContainer == null) {
                    fcmListenerContainer = applicationContext.getBean("fcmOnlyListenerContainer", RedisMessageListenerContainer.class);
                }
                
                // RedisPubSubService 빈 획득
                Object listener = applicationContext.getBean("redisPubSubService");
                
                // FCM 채널 구독 추가
                fcmListenerContainer.addMessageListener((org.springframework.data.redis.connection.MessageListener) listener, 
                                                       new PatternTopic("fcm:*"));
                isFcmChannelSubscribed = true;
                log.info("FCM 채널 구독 성공: {}", serverInstanceId);
            } catch (Exception e) {
                log.error("FCM 채널 구독 실패: {} - 원인: {}", serverInstanceId, e.getMessage());
                // 구독 실패해도 마스터 상태는 유지 (shouldProcessFcm()로 처리)
            }
        }
    }
    
    /**
     * FCM 채널 구독 해제
     */
    private void unsubscribeFcmChannels() {
        if (isFcmChannelSubscribed) {
            try {
                // FCM 리스너 컨테이너 지연 로딩
                if (fcmListenerContainer == null) {
                    fcmListenerContainer = applicationContext.getBean("fcmOnlyListenerContainer", RedisMessageListenerContainer.class);
                }
                
                // RedisPubSubService 빈 획득
                Object listener = applicationContext.getBean("redisPubSubService");
                
                // FCM 채널 구독 해제
                fcmListenerContainer.removeMessageListener((org.springframework.data.redis.connection.MessageListener) listener, 
                                                          new PatternTopic("fcm:*"));
                isFcmChannelSubscribed = false;
                log.info("FCM 채널 구독 해제 성공: {}", serverInstanceId);
            } catch (Exception e) {
                log.error("FCM 채널 구독 해제 실패: {} - 원인: {}", serverInstanceId, e.getMessage());
                // 해제 실패해도 상태는 false로 변경하여 재시도 가능하게 함
                isFcmChannelSubscribed = false;
            }
        }
    }
    
    /**
     * 서버 종료 시 FCM 마스터 키 정리
     */
    @PreDestroy
    public void cleanup() {
        if (isFcmMasterServer) {
            try {
                // FCM 채널 구독 해제
                unsubscribeFcmChannels();
                
                // 현재 서버가 마스터인 경우에만 Redis에서 마스터 키 삭제
                String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
                if (serverInstanceId.equals(currentMaster)) {
                    redisTemplate.delete(FCM_MASTER_KEY);
                    log.info("FCM 마스터 키 정리 완료: {}", serverInstanceId);
                } else {
                    log.debug("FCM 마스터 키 정리 스킵 - 현재 마스터가 아님: current={}, expected={}", 
                        currentMaster, serverInstanceId);
                }
            } catch (Exception e) {
                log.error("FCM 마스터 키 정리 실패: {}", serverInstanceId, e);
            }
        }
    }
}