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
import org.springframework.context.SmartLifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ServerInstanceUtil implements SmartLifecycle {
    
    private final String serverInstanceId;
    private volatile boolean isFcmMaster = false;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationContext applicationContext;
    
    private static final String FCM_MASTER_KEY = "fcm:master:server";
    private static final Duration MASTER_TTL = Duration.ofMinutes(3);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofMinutes(1);
    
    // FCM 리스너 컨테이너
    private RedisMessageListenerContainer fcmListenerContainer;
    private final AtomicBoolean fcmChannelSubscribed = new AtomicBoolean(false);
    private volatile boolean applicationReady = false;
    
    // 단일 스케줄러로 모든 작업 처리
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "fcm-master-scheduler");
        t.setDaemon(true);
        return t;
    });
    
    // SmartLifecycle 구현을 위한 상태 관리
    private volatile boolean running = false;
    
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
     * 마스터 상태 유지 및 재선출
     */
    private void maintainMasterStatus() {
        try {
            String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
            
            if (isFcmMaster) {
                // 마스터인 경우: 소유권 검증 후 TTL 갱신
                if (serverInstanceId.equals(currentMaster)) {
                    redisTemplate.expire(FCM_MASTER_KEY, MASTER_TTL);
                    log.debug("FCM 마스터 TTL 갱신: {}", serverInstanceId);
                } else {
                    log.warn("FCM 마스터 소유권 상실 감지: {} -> {}, 마스터 해제", serverInstanceId, currentMaster);
                    demoteMaster();
                }
            } else {
                // 비마스터인 경우: 마스터가 없으면 재선출 시도
                if (currentMaster == null) {
                    log.info("FCM 마스터 부재 감지, 재선출 시도: {}", serverInstanceId);
                    attemptMasterRegistration();
                }
            }
        } catch (Exception e) {
            log.error("마스터 상태 유지 중 오류", e);
        }
    }
    
    /**
     * 마스터 재선출 시도
     */
    private void attemptMasterRegistration() {
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                FCM_MASTER_KEY, 
                serverInstanceId, 
                MASTER_TTL
            );
            
            if (Boolean.TRUE.equals(success)) {
                becomeMaster();
                log.info("FCM 마스터 재선출 성공: {}", serverInstanceId);
            } else {
                String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
                log.debug("FCM 마스터 재선출 실패, 기존 마스터: {}", currentMaster);
            }
        } catch (Exception e) {
            log.error("FCM 마스터 재선출 실패", e);
        }
    }
    
    /**
     * 마스터에서 해제 (소유권 상실 시)
     */
    private void demoteMaster() {
        if (isFcmMaster) {
            isFcmMaster = false;
            log.info("FCM 마스터 권한 해제: {}", serverInstanceId);
            
            // FCM 채널 구독 해제
            unsubscribeFcmChannels();
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
        if (isFcmMaster && !fcmChannelSubscribed.get()) {
            subscribeFcmChannels();
        }
    }
    
    /**
     * FCM 채널 구독 시작 (원자적 가드)
     */
    private void subscribeFcmChannels() {
        // 원자적 CAS로 중복 구독 방지
        if (!fcmChannelSubscribed.compareAndSet(false, true)) {
            log.debug("FCM 채널 이미 구독됨, 스킵: {}", serverInstanceId);
            return;
        }
        
        try {
            if (fcmListenerContainer == null) {
                fcmListenerContainer = applicationContext.getBean("fcmOnlyListenerContainer", RedisMessageListenerContainer.class);
            }
            
            Object listener = applicationContext.getBean("redisPubSubService");
            PatternTopic fcmTopic = new PatternTopic("fcm:*");
            
            // 기존 리스너가 있다면 먼저 제거 (remove-then-add 패턴)
            try {
                fcmListenerContainer.removeMessageListener(
                    (org.springframework.data.redis.connection.MessageListener) listener, 
                    fcmTopic
                );
            } catch (Exception e) {
                log.debug("기존 FCM 리스너 제거 시 예외 (무시): {}", e.getMessage());
            }
            
            // 새 리스너 등록
            fcmListenerContainer.addMessageListener(
                (org.springframework.data.redis.connection.MessageListener) listener, 
                fcmTopic
            );
            
            log.info("FCM 채널 구독 성공: {}", serverInstanceId);
        } catch (Exception e) {
            log.error("FCM 채널 구독 실패: {}", serverInstanceId, e);
            // 실패 시 상태 복구
            fcmChannelSubscribed.set(false);
        }
    }
    
    /**
     * FCM 채널 구독 해제 (원자적 가드)
     */
    private void unsubscribeFcmChannels() {
        // 원자적 CAS로 중복 해제 방지
        if (!fcmChannelSubscribed.compareAndSet(true, false)) {
            log.debug("FCM 채널 이미 구독 해제됨, 스킵: {}", serverInstanceId);
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
            
            log.info("FCM 채널 구독 해제 성공: {}", serverInstanceId);
        } catch (Exception e) {
            log.error("FCM 채널 구독 해제 실패: {}", serverInstanceId, e);
            // 실패하더라도 상태는 이미 false로 설정됨
        }
    }
    
    @Override
    public void start() {
        if (!running) {
            running = true;
            log.info("FCM ServerInstanceUtil 시작: {}", serverInstanceId);
        }
    }

    @Override
    public void stop() {
        if (running) {
            stop(() -> log.info("FCM ServerInstanceUtil 정상 종료 완료: {}", serverInstanceId));
        }
    }

    @Override
    public void stop(Runnable callback) {
        if (!running) {
            callback.run();
            return;
        }
        
        running = false;
        log.info("FCM 서버 인스턴스 정리 시작 (SmartLifecycle): {}", serverInstanceId);
        
        // FCM 채널 구독 해제
        if (isFcmMaster) {
            unsubscribeFcmChannels();
            
            // 마스터 키 정리
            cleanupMasterKey();
        }
        
        // 스케줄러 종료
        shutdownScheduler();
        
        log.info("FCM 서버 인스턴스 정리 완료 (SmartLifecycle): {}", serverInstanceId);
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 더 이른 시점에 종료되도록 높은 우선순위 설정 (기본값 0보다 높음)
        return Integer.MAX_VALUE - 1000;  // Redis 등 인프라보다 먼저 종료
    }

    @Override
    public boolean isAutoStartup() {
        return true;  // 애플리케이션 시작 시 자동으로 start() 호출
    }
    
    /**
     * 마스터 키 정리 (별도 메서드로 분리)
     */
    private void cleanupMasterKey() {
        try {
            String currentMaster = redisTemplate.opsForValue().get(FCM_MASTER_KEY);
            if (serverInstanceId.equals(currentMaster)) {
                redisTemplate.delete(FCM_MASTER_KEY);
                log.info("FCM 마스터 키 정리 완료: {}", serverInstanceId);
            }
        } catch (Exception e) {
            log.warn("FCM 마스터 키 정리 실패: {}", e.getMessage());
        }
    }
    
    /**
     * 스케줄러 종료 (별도 메서드로 분리)
     */
    private void shutdownScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("스케줄러 종료 실패: {}", serverInstanceId);
                }
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * PreDestroy는 SmartLifecycle의 백업으로만 사용
     */
    @PreDestroy
    public void preDestroyBackup() {
        if (running) {
            log.warn("SmartLifecycle이 실행되지 않음, PreDestroy로 백업 정리 수행: {}", serverInstanceId);
            stop();
        }
    }
}