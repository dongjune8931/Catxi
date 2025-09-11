package com.project.catxi.fcm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.fcm.util.FcmBusinessKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FcmQueueService {
    
    private static final String FCM_QUEUE_KEY = "fcm:queue";
    private static final String FCM_PROCESSING_KEY_PREFIX = "fcm:processing:";
    private static final int PROCESSING_TTL_SECONDS = 60;
    
    // Lua 스크립트로 원자적 연산 최적화
    private static final String ENQUEUE_SCRIPT = 
        "if redis.call('setnx', KEYS[2], '1') == 1 then " +
        "    redis.call('expire', KEYS[2], ARGV[2]) " +
        "    redis.call('rpush', KEYS[1], ARGV[1]) " +
        "    return 1 " +
        "else " +
        "    return 0 " +
        "end";
    
    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FcmBusinessKeyGenerator fcmBusinessKeyGenerator;
    
    private final DefaultRedisScript<Long> enqueueScript;
    
    // 생성자에서 스크립트 초기화
    public FcmQueueService(
            @Qualifier("chatPubSub") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            FcmBusinessKeyGenerator fcmBusinessKeyGenerator) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.fcmBusinessKeyGenerator = fcmBusinessKeyGenerator;
        
        // Lua 스크립트 초기화
        this.enqueueScript = new DefaultRedisScript<>();
        this.enqueueScript.setScriptText(ENQUEUE_SCRIPT);
        this.enqueueScript.setResultType(Long.class);
    }
    
    /**
     * FCM 알림을 큐에 추가 (중복 방지) - Lua 스크립트로 원자적 처리
     */
    public boolean enqueueFcmEvent(FcmNotificationEvent event) {
        try {
            // BusinessKey 생성
            String businessKey = fcmBusinessKeyGenerator.generateBusinessKey(event);
            FcmNotificationEvent eventWithKey = event.withBusinessKey(businessKey);

            log.info("FCM 큐 이벤트 추가 시도 - EventId: {}, BusinessKey: {}, Targets: {}", 
                    eventWithKey.eventId(), eventWithKey.businessKey(), eventWithKey.targetMemberIds().size());
            
            // JSON 직렬화
            String eventJson = objectMapper.writeValueAsString(eventWithKey);
            String processingKey = FCM_PROCESSING_KEY_PREFIX + businessKey;
            
            // Lua 스크립트로 원자적 처리 (중복 체크 + 큐 추가)
            Long result = redisTemplate.execute(enqueueScript, 
                List.of(FCM_QUEUE_KEY, processingKey), 
                eventJson, String.valueOf(PROCESSING_TTL_SECONDS));
            
            if (result != null && result.equals(1L)) {
                log.info("FCM 큐 이벤트 추가 완료 - EventId: {}, BusinessKey: {}", 
                        eventWithKey.eventId(), eventWithKey.businessKey());
                return true;
            } else {
                log.debug("FCM 이벤트 중복 방지 - BusinessKey: {}", businessKey);
                return false;
            }
                    
        } catch (JsonProcessingException e) {
            log.error("FCM 이벤트 직렬화 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("FCM 큐 이벤트 추가 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 큐에서 FCM 이벤트 하나 가져오기
     */
    public FcmNotificationEvent dequeueFcmEvent() {
        try {
            // 큐에서 왼쪽에서 팝 (블로킹, 5초 타임아웃)
            String eventJson = redisTemplate.opsForList().leftPop(FCM_QUEUE_KEY, 5, TimeUnit.SECONDS);
            
            if (eventJson == null) {
                return null; // 타임아웃 또는 빈 큐
            }
            
            // JSON을 객체로 역직렬화
            FcmNotificationEvent event = objectMapper.readValue(eventJson, FcmNotificationEvent.class);
            
            log.debug("FCM 큐에서 이벤트 가져옴 - EventId: {}, BusinessKey: {}", 
                    event.eventId(), event.businessKey());
            
            return event;
            
        } catch (Exception e) {
            // Redis 연결 종료 관련 예외는 별도 처리
            if (e.getMessage() != null && 
                (e.getMessage().contains("LettuceConnectionFactory has been STOPPED") ||
                 e.getMessage().contains("Connection factory shut down"))) {
                log.info("FCM 큐 서비스 종료 중 - Redis 연결 이미 종료됨");
                return null; // 정상적인 종료 상황으로 처리
            }
            log.error("FCM 큐에서 이벤트 가져오기 실패", e);
            return null;
        }
    }
    
    /**
     * 이벤트 처리 완료 마크
     */
    public void markEventCompleted(String businessKey) {
        try {
            String processingKey = FCM_PROCESSING_KEY_PREFIX + businessKey;
            // 비동기적으로 키 삭제
            redisTemplate.unlink(processingKey);
            log.debug("FCM 이벤트 처리 완료 마크 - BusinessKey: {}", businessKey);
        } catch (Exception e) {
            log.error("FCM 이벤트 완료 마크 실패 - BusinessKey: {}", businessKey, e);
        }
    }
    
    /**
     * 채팅 메시지 알림 이벤트 발행
     */
    public void publishChatNotification(Long targetMemberId, Long roomId, Long messageId, String senderNickname, String message) {
        FcmNotificationEvent event = FcmNotificationEvent.createChatMessage(
                targetMemberId, roomId, messageId, senderNickname, message);
        enqueueFcmEvent(event);
    }
    
    /**
     * 준비 요청 알림 이벤트 발행
     */
    public void publishReadyRequestNotification(java.util.List<Long> targetMemberIds, Long roomId) {
        FcmNotificationEvent event = FcmNotificationEvent.createReadyRequest(
                targetMemberIds, roomId);
        enqueueFcmEvent(event);
    }
}