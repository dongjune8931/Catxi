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
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ServerInstanceUtil {
    
    private final String serverInstanceId;
    private volatile boolean isFcmMaster = false;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;
    
    private static final String FCM_MASTER_KEY = "fcm:master:server";
    private static final Duration MASTER_TTL = Duration.ofMinutes(3);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(1);
    
    // FCM 리스너 컨테이너
    private RedisMessageListenerContainer fcmListenerContainer;
    private volatile boolean fcmChannelSubscribed = false;
    private volatile boolean applicationReady = false;
    
    // 단일 스케줄러로 모든 작업 처리
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fcm-master-scheduler");
        t.setDaemon(true);
        return t;
    });
    
    public ServerInstanceUtil(@Qualifier("chatPubSub") StringRedisTemplate redisTemplate, 
                             ApplicationContext applicationContext) {
        this.redisTemplate = redisTemplate;
        this.applicationContext = applicationContext;
        this.serverInstanceId = generateServerId();
    }
    
    @PostConstruct
    public void init() {
        log.info("서버 인스턴스 ID 초기화: {}", serverInstanceId);
        
        forceMasterRegistration();
    }
    
    /**
     * 서버 ID 생성
     */
    private String generateServerId() {
        try {
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && !hostname.isEmpty()) {
                return hostname;
            }
        } catch (Exception e) {
            log.debug("HOSTNAME 환경변수 조회 실패", e);
        }
        
        // fallback: UUID 기반 단축 ID
        return "server-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * 첫 번째 서버만 FCM 마스터로 등록
     */
    private void forceMasterRegistration() {
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                FCM_MASTER_KEY, 
                serverInstanceId, 
                MASTER_TTL
            );
            
            if (Boolean.TRUE.equals(success)) {
                becomeMaster();
                log.info("FCM 마스터 등록 성공 (첫 번째 서버): {}", serverInstanceId);
            } else {
                String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
                log.info("FCM 마스터가 이미 존재함: {}", currentMaster);
            }
            
        } catch (Exception e) {
            log.error("FCM 마스터 등록 실패", e);
        }
    }

    
    /**
     * 마스터로 전환
     */
    private void becomeMaster() {
        if (!isFcmMaster) {
            isFcmMaster = true;
            log.info("FCM 마스터 서버로 등록 성공: {}", serverInstanceId);
            
            // FCM 채널 구독 (ApplicationReady 이후에만)
            if (applicationReady) {
                subscribeFcmChannels();
            }
            
            // 하트비트 스케줄러 시작
            startHeartbeat();
        }
    }
    
    
    /**
     * 마스터 하트비트 스케줄러 시작
     */
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::maintainMasterStatus, 
                                      HEARTBEAT_INTERVAL.toSeconds(), 
                                      HEARTBEAT_INTERVAL.toSeconds(), 
                                      TimeUnit.SECONDS);
    }
    
    /**
     * 마스터 TTL 갱신
     */
    private void maintainMasterStatus() {
        try {
            if (isFcmMaster) {
                redisTemplate.expire(FCM_MASTER_KEY, MASTER_TTL);
                log.debug("FCM 마스터 TTL 갱신: {}", serverInstanceId);
            }
        } catch (Exception e) {
            log.error("마스터 TTL 갱신 실패", e);
        }
    }
    
    /**
     * 현재 서버가 FCM 처리를 담당하는지 확인
     */
    public boolean shouldProcessFcm() {
        return isFcmMaster;
    }
    
    /**
     * 현재 서버 인스턴스 ID 반환
     */
    public String getServerInstanceId() {
        return serverInstanceId;
    }
    
    /**
     * 애플리케이션 준비 완료 시 FCM 채널 구독 처리
     */
    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        applicationReady = true;
        log.info("Application 준비 완료, FCM 마스터 상태: {}", isFcmMaster);
        
        // 마스터인 경우 FCM 채널 구독
        if (isFcmMaster && !fcmChannelSubscribed) {
            subscribeFcmChannels();
        }
    }
    
    /**
     * FCM 채널 구독 시작
     */
    private void subscribeFcmChannels() {
        if (fcmChannelSubscribed) {
            return;
        }
        
        try {
            if (fcmListenerContainer == null) {
                fcmListenerContainer = applicationContext.getBean("fcmOnlyListenerContainer", RedisMessageListenerContainer.class);
            }
            
            Object listener = applicationContext.getBean("redisPubSubService");
            fcmListenerContainer.addMessageListener(
                (org.springframework.data.redis.connection.MessageListener) listener, 
                new PatternTopic("fcm:*")
            );
            
            fcmChannelSubscribed = true;
            log.info("FCM 채널 구독 성공: {}", serverInstanceId);
        } catch (Exception e) {
            log.error("FCM 채널 구독 실패: {}", serverInstanceId, e);
        }
    }
    
    /**
     * FCM 채널 구독 해제
     */
    private void unsubscribeFcmChannels() {
        if (!fcmChannelSubscribed) {
            return;
        }
        
        try {
            if (fcmListenerContainer == null) {
                fcmListenerContainer = applicationContext.getBean("fcmOnlyListenerContainer", RedisMessageListenerContainer.class);
            }
            
            Object listener = applicationContext.getBean("redisPubSubService");
            fcmListenerContainer.removeMessageListener(
                (org.springframework.data.redis.connection.MessageListener) listener, 
                new PatternTopic("fcm:*")
            );
            
            fcmChannelSubscribed = false;
            log.info("FCM 채널 구독 해제 성공: {}", serverInstanceId);
        } catch (Exception e) {
            log.error("FCM 채널 구독 해제 실패: {}", serverInstanceId, e);
            fcmChannelSubscribed = false;
        }
    }
    
    /**
     * 서버 종료 시 정리
     */
    @PreDestroy
    public void cleanup() {
        log.info("FCM 서버 인스턴스 정리 시작: {}", serverInstanceId);
        
        // 스케줄러 종료
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // FCM 채널 구독 해제
        if (isFcmMaster) {
            unsubscribeFcmChannels();
            
            // 마스터 키 정리
            try {
                String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
                if (serverInstanceId.equals(currentMaster)) {
                    redisTemplate.delete(FCM_MASTER_KEY);
                    log.info("FCM 마스터 키 정리 완료: {}", serverInstanceId);
                }
            } catch (Exception e) {
                log.warn("FCM 마스터 키 정리 실패 (무시): {}", e.getMessage());
            }
        }
        
        log.info("FCM 서버 인스턴스 정리 완료: {}", serverInstanceId);
    }
}