package com.project.catxi.fcm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.fcm.util.FcmBusinessKeyGenerator;
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
    private final FcmBusinessKeyGenerator fcmBusinessKeyGenerator;
    
    /**
     * FCM 알림 이벤트를 Redis PubSub에 발행 (분산락으로 중복 처리 방지)
     */
    public void publishFcmEvent(FcmNotificationEvent event) {
        try {
            // BusinessKey 생성 및 이벤트 업데이트
            String businessKey = fcmBusinessKeyGenerator.generateBusinessKey(event);
            FcmNotificationEvent eventWithKey = event.withBusinessKey(businessKey);

            log.info("FCM 이벤트 발행 시도 - EventId: {}, Type: {}, BusinessKey: {}, Targets: {}", 
                    eventWithKey.eventId(), eventWithKey.type(), eventWithKey.businessKey(), eventWithKey.targetMemberIds().size());
            
            String eventJson = objectMapper.writeValueAsString(eventWithKey);
            redisTemplate.convertAndSend(FCM_CHANNEL, eventJson);
            
            log.info("FCM 이벤트 발행 완료 - EventId: {}, BusinessKey: {}, Type: {}", 
                    eventWithKey.eventId(), eventWithKey.businessKey(), eventWithKey.type());
                    
        } catch (JsonProcessingException e) {
            log.error("FCM 이벤트 직렬화 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("FCM 이벤트 발행 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
        }
    }
    
    /**
     * 채팅 메시지 알림 이벤트 발행
     */
    public void publishChatNotification(Long targetMemberId, Long roomId, String senderNickname, String message) {
        FcmNotificationEvent event = FcmNotificationEvent.createChatMessage(
                targetMemberId, roomId, senderNickname, message);
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