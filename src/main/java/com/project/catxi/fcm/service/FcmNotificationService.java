package com.project.catxi.fcm.service;

import com.google.firebase.messaging.*;
import com.project.catxi.member.domain.Member;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

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
     * 채팅 메시지 알림 발송
     * @param targetMember 알림을 받을 사용자
     * @param senderNickname 메시지 발송자 닉네임
     * @param message 채팅 내용
     */
    public CompletableFuture<Void> sendChatNotification(Member targetMember, String senderNickname, String message) {
        return CompletableFuture.runAsync(() -> {
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
                }
            } catch (Exception e) {
                log.error("채팅 알림 발송 실패 - Member ID: {}", targetMember.getId(), e);
            }
        });
    }

    /**
     * 방장의 준비요청 알림 발송
     * @param targetMembers 알림을 받을 사용자들
     * @param roomId 채팅방 ID
     */
    public CompletableFuture<Void> sendReadyRequestNotification(List<Member> targetMembers, Long roomId) {
        return CompletableFuture.runAsync(() -> {
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
            } catch (Exception e) {
                log.error("준비요청 알림 발송 실패 - Room ID: {}", roomId, e);
            }
        });
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

            Notification notification = Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build();

            MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                    .setNotification(notification)
                    .addAllTokens(tokens)
                    .putData("type", type);

            if (roomId != null) {
                messageBuilder.putData("roomId", roomId.toString());
            }

            MulticastMessage message = messageBuilder.build();

            BatchResponse response = firebaseMessaging.sendMulticast(message);
            
            log.info("FCM 알림 발송 완료 - 성공: {}, 실패: {}, 타입: {}", 
                    response.getSuccessCount(), response.getFailureCount(), type);

            // 실패한 토큰들 로깅 (토큰 만료 등의 이유로 실패할 수 있음)
            if (response.getFailureCount() > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    if (!responses.get(i).isSuccessful()) {
                        log.warn("FCM 토큰 발송 실패 - Token: {}, Error: {}", 
                                tokens.get(i).substring(0, Math.min(20, tokens.get(i).length())) + "...",
                                responses.get(i).getException().getMessage());
                    }
                }
            }

        } catch (FirebaseMessagingException e) {
            log.error("Firebase 메시징 오류: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("FCM 알림 발송 중 예외 발생: {}", e.getMessage(), e);
        }
    }
}