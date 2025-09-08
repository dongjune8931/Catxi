package com.project.catxi.fcm.service;

import com.google.firebase.messaging.*;
import com.project.catxi.member.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmNotificationService {

    private final FirebaseMessaging firebaseMessaging;
    private final FcmTokenService fcmTokenService;
    
    private boolean isFirebaseInitialized() {
        return firebaseMessaging != null;
    }

    /**
     * 채팅 메시지 알림 발송 (동기 - Redis Consumer에서 호출용)
     * @param targetMember 알림을 받을 사용자
     * @param senderNickname 메시지 발송자 닉네임
     * @param message 채팅 내용
     */
    public void sendChatNotificationSync(Member targetMember, String senderNickname, String message) {
        try {
            if (!isFirebaseInitialized()) {
                log.warn("Firebase가 초기화되지 않아 채팅 알림을 발송할 수 없습니다.");
                return;
            }
            
            List<String> tokens = fcmTokenService.getActiveTokens(targetMember);
            if (!tokens.isEmpty()) {
                String title = "새로운 채팅 메시지";
                String body = String.format("%s: %s", senderNickname, message);
                sendMulticastNotification(tokens, title, body, "CHAT");
                log.debug("채팅 알림 발송 완료 - Member ID: {}", targetMember.getId());
            }
            
        } catch (Exception e) {
            log.error("채팅 알림 발송 실패 - Member ID: {}", targetMember.getId(), e);
        }
    }

    /**
     * 방장의 준비요청 알림 발송
     * @param targetMembers 알림을 받을 사용자들
     * @param roomId 채팅방 ID
     */
    public void sendReadyRequestNotificationSync(List<Member> targetMembers, Long roomId) {
        try {
            if (!isFirebaseInitialized()) {
                log.warn("Firebase가 초기화되지 않아 준비요청 알림을 발송할 수 없습니다.");
                return;
            }
            
            String title = "준비 요청";
            String body = "방장이 준비요청을 보냈습니다";

            for (Member member : targetMembers) {
                List<String> tokens = fcmTokenService.getActiveTokens(member);
                if (!tokens.isEmpty()) {
                    sendMulticastNotification(tokens, title, body, "READY_REQUEST", roomId);
                }
            }
            
            log.debug("준비요청 알림 발송 완료 - Room ID: {}, Targets: {}", roomId, targetMembers.size());
            
        } catch (Exception e) {
            log.error("준비요청 알림 발송 실패 - Room ID: {}", roomId, e);
        }
    }

    /**
     * 멀티캐스트 알림 발송
     */
    private void sendMulticastNotification(List<String> tokens, String title, String body, String type) {
        sendMulticastNotification(tokens, title, body, type, null);
    }

    private void sendMulticastNotification(List<String> tokens, String title, String body, String type, Long roomId) {
        try {
            if (!isFirebaseInitialized()) {
                log.warn("FirebaseMessaging이 초기화되지 않아 알림을 발송할 수 없습니다.");
                return;
            }

            // 토큰 유효성 검사
            List<String> validTokens = tokens.stream()
                    .filter(this::isValidFcmToken)
                    .toList();

            if (validTokens.isEmpty()) {
                log.warn("유효한 FCM 토큰이 없습니다.");
                return;
            }

            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // 개별 토큰으로 전송
            for (String token : validTokens) {
                Message.Builder messageBuilder = Message.builder()
                        .setToken(token)
                        .setNotification(notification)
                        .putData("type", type);

                if (roomId != null) {
                    messageBuilder.putData("roomId", roomId.toString());
                }

                Message message = messageBuilder.build();

                try {
                    String response = firebaseMessaging.send(message);
                    successCount.incrementAndGet();
                    log.debug("FCM 메시지 전송 성공 - Response: {}", response);
                } catch (FirebaseMessagingException e) {
                    failureCount.incrementAndGet();
                    log.warn("FCM 토큰 발송 실패 - Token: {}, Error: {}", 
                            token.substring(0, Math.min(20, token.length())) + "...",
                            e.getMessage());
                    
                    // 토큰 만료나 잘못된 토큰의 경우 정리
                    if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                        e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT) {
                        log.info("유효하지 않은 FCM 토큰 발견: {}", 
                                token.substring(0, Math.min(20, token.length())) + "...");
                        // 유효하지 않은 토큰을 DB에서 제거
                        fcmTokenService.removeInvalidFcmToken(token);
                    }
                }
            }
            
            log.info("FCM 알림 발송 완료 - 성공: {}, 실패: {}, 타입: {}", 
                    successCount.get(), failureCount.get(), type);

        } catch (Exception e) {
            log.error("FCM 알림 발송 중 예외 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * FCM 토큰 유효성 검사
     */
    private boolean isValidFcmToken(String token) {
        return token != null && !token.trim().isEmpty() && token.length() > 100;
    }
}