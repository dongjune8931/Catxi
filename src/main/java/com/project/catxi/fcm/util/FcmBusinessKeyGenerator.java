package com.project.catxi.fcm.util;

import com.project.catxi.fcm.dto.FcmNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FcmBusinessKeyGenerator {
    
    /**
     * FCM 이벤트의 비즈니스 로직 기반 고유 키 생성
     */
    public String generateBusinessKey(FcmNotificationEvent event) {
        try {
            switch (event.type()) {
                case CHAT_MESSAGE:
                    return generateChatMessageKey(event);
                case READY_REQUEST:
                    return generateReadyRequestKey(event);
                case SYSTEM_NOTIFICATION:
                    return generateSystemNotificationKey(event);
                default:
                    log.warn("알 수 없는 FCM 이벤트 타입 - Type: {}, EventId: {}", 
                            event.type(), event.eventId());
                    return "unknown:" + event.eventId();
            }
        } catch (Exception e) {
            log.error("FCM 비즈니스 키 생성 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
            return "error:" + event.eventId();
        }
    }
    
    /**
     * 채팅 메시지 FCM 키 생성
     * 형식: chat:{roomId}:{targetMemberId}:{messageId}
     */
    private String generateChatMessageKey(FcmNotificationEvent event) {
        String roomId = event.data().get("roomId");
        String messageId = event.data().get("messageId");
        Long targetMemberId = event.targetMemberIds().get(0);
        
        // 메시지 ID 기반 고유 키 생성 (단순화)
        if (messageId != null && !messageId.isEmpty()) {
            return String.format("chat:%s:%d:%s", roomId, targetMemberId, messageId);
        } else {
            // fallback: eventId 사용
            return String.format("chat:%s:%d:%s", roomId, targetMemberId, event.eventId());
        }
    }
    
    /**
     * 준비 요청 FCM 키 생성
     * 형식: ready:{roomId}:{eventId}
     */
    private String generateReadyRequestKey(FcmNotificationEvent event) {
        String roomId = event.data().get("roomId");
        
        // EventId 기반으로 단순화 (더 정확한 중복 방지)
        return String.format("ready:%s:%s", roomId, event.eventId());
    }
    
    /**
     * 시스템 알림 FCM 키 생성
     * 형식: system:{targetMemberId}:{eventId}
     */
    private String generateSystemNotificationKey(FcmNotificationEvent event) {
        Long targetMemberId = event.targetMemberIds().get(0);
        
        // EventId 기반으로 단순화
        return String.format("system:%d:%s", targetMemberId, event.eventId());
    }
    
}