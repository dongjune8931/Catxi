package com.project.catxi.fcm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.fcm.util.FcmBusinessKeyGenerator;
import com.project.catxi.common.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmEventPublisher {
    
    private static final String FCM_CHANNEL = "fcm-notifications";
    
    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisDistributedLockService distributedLockService;
    private final FcmBusinessKeyGenerator businessKeyGenerator;
    
    /**
     * FCM 알림 이벤트를 Redis에 발행 (분산락으로 중복 발행 방지)
     */
    public void publishFcmEvent(FcmNotificationEvent event) {
        try {
            // 비즈니스 로직 기반 분산락 키 생성
            String businessLockKey = businessKeyGenerator.generateBusinessKey(event);
            String publishLockKey = "publish:" + businessLockKey;
            
            log.info("FCM 이벤트 발행 시도 - EventId: {}, Type: {}, BusinessKey: {}, Targets: {}", 
                    event.eventId(), event.type(), businessLockKey, event.targetMemberIds().size());
            
            // 분산락을 사용하여 중복 발행 방지 (10초 TTL)
            boolean published = distributedLockService.executeWithLock(
                publishLockKey,
                10, // 10초 락 TTL
                () -> {
                    try {
                        String eventJson = objectMapper.writeValueAsString(event);
                        redisTemplate.convertAndSend(FCM_CHANNEL, eventJson);
                        
                        log.info("FCM 이벤트 발행 완료 - EventId: {}, BusinessKey: {}, Type: {}", 
                                event.eventId(), businessLockKey, event.type());
                    } catch (JsonProcessingException e) {
                        log.error("FCM 이벤트 직렬화 실패 - EventId: {}, Error: {}", 
                                event.eventId(), e.getMessage(), e);
                        throw new RuntimeException("FCM 이벤트 직렬화 실패", e);
                    }
                }
            );
            
            if (!published) {
                log.debug("FCM 이벤트 중복 발행 방지 (분산락) - EventId: {}, BusinessKey: {}", 
                        event.eventId(), businessLockKey);
            }
                    
        } catch (Exception e) {
            log.error("FCM 이벤트 발행 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
        }
    }
    
    /**
     * 채팅 메시지 알림 이벤트 발행
     */
    public void publishChatNotification(Long targetMemberId, String senderNickname, String message) {
        FcmNotificationEvent event = FcmNotificationEvent.createChatMessage(
                targetMemberId, senderNickname, message);
        publishFcmEvent(event);
    }
    
    /**
     * 준비 요청 알림 이벤트 발행
     */
    public void publishReadyRequestNotification(java.util.List<Long> targetMemberIds, Long roomId) {
        FcmNotificationEvent event = FcmNotificationEvent.createReadyRequest(
                targetMemberIds, roomId);
        publishFcmEvent(event);
    }
}