package com.project.catxi.fcm.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record FcmNotificationEvent(
    @JsonProperty("eventId") String eventId,
    @JsonProperty("businessKey") String businessKey,
    @JsonProperty("type") NotificationType type,
    @JsonProperty("targetMemberIds") List<Long> targetMemberIds,
    @JsonProperty("title") String title,
    @JsonProperty("body") String body,
    @JsonProperty("data") Map<String, String> data,
    @JsonProperty("createdAt") LocalDateTime createdAt,
    @JsonProperty("retryCount") int retryCount
) {
    
    // 단일 사용자용 정적 팩토리 메서드
    public static FcmNotificationEvent createChatMessage(Long targetMemberId, String senderNickname, String message) {
        String eventId = UUID.randomUUID().toString();
        String businessKey = String.format("chat:%s:%s", targetMemberId, System.currentTimeMillis());
        
        return new FcmNotificationEvent(
                eventId,
                businessKey,
                NotificationType.CHAT_MESSAGE,
                List.of(targetMemberId),
                "새로운 채팅 메시지",
                String.format("%s: %s", senderNickname, message),
                Map.of("type", "CHAT"),
                LocalDateTime.now(),
                0
        );
    }
    
    // 다중 사용자용 정적 팩토리 메서드  
    public static FcmNotificationEvent createReadyRequest(List<Long> targetMemberIds, Long roomId) {
        String eventId = UUID.randomUUID().toString();
        String businessKey = String.format("ready:%s:%s", roomId, System.currentTimeMillis());
        
        return new FcmNotificationEvent(
                eventId,
                businessKey,
                NotificationType.READY_REQUEST,
                targetMemberIds,
                "준비 요청",
                "방장이 준비요청을 보냈습니다",
                Map.of("type", "READY_REQUEST", "roomId", roomId.toString()),
                LocalDateTime.now(),
                0
        );
    }
    
    public enum NotificationType {
        CHAT_MESSAGE,
        READY_REQUEST,
        SYSTEM_NOTIFICATION
    }
}