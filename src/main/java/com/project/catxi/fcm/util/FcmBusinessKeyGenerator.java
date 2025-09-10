package com.project.catxi.fcm.util;

import com.project.catxi.fcm.dto.FcmNotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

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
     * 형식: chat:{roomId}:{targetMemberId}:{timeWindow}:{messageHash}
     */
    private String generateChatMessageKey(FcmNotificationEvent event) {
        String roomId = event.data().get("roomId");
        Long targetMemberId = event.targetMemberIds().get(0);
        
        // 1분 단위 시간 윈도우 (같은 메시지가 1분 내 중복 발송 방지)
        long timeWindow = System.currentTimeMillis() / 60000;
        
        // 메시지 내용 기반 해시 (같은 내용 중복 방지)
        String messageHash = generateHash(event.body());
        
        return String.format("chat:%s:%d:%d:%s", roomId, targetMemberId, timeWindow, messageHash);
    }
    
    /**
     * 준비 요청 FCM 키 생성
     * 형식: ready:{roomId}:{timeWindow}
     */
    private String generateReadyRequestKey(FcmNotificationEvent event) {
        String roomId = event.data().get("roomId");
        
        // 30초 단위 시간 윈도우 (준비요청은 30초 내 중복 발송 방지)
        long timeWindow = System.currentTimeMillis() / 30000;
        
        return String.format("ready:%s:%d", roomId, timeWindow);
    }
    
    /**
     * 시스템 알림 FCM 키 생성
     * 형식: system:{targetMemberId}:{timeWindow}:{contentHash}
     */
    private String generateSystemNotificationKey(FcmNotificationEvent event) {
        Long targetMemberId = event.targetMemberIds().get(0);
        
        // 5분 단위 시간 윈도우
        long timeWindow = System.currentTimeMillis() / 300000;
        
        String contentHash = generateHash(event.title() + ":" + event.body());
        
        return String.format("system:%d:%d:%s", targetMemberId, timeWindow, contentHash);
    }
    
    /**
     * 문자열의 SHA-256 해시 생성 (앞 8자리만 사용)
     */
    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // 해시의 앞 8자리만 사용 (키 길이 단축)
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(4, hash.length); i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 해시 생성 실패: {}", e.getMessage());
            // 해시 생성 실패 시 입력 문자열의 해시코드 사용
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }
    
}