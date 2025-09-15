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

            // 배치 전송을 위한 토큰 분할 (최대 500개씩)
            final int BATCH_SIZE = 500;
            AtomicInteger totalSuccessCount = new AtomicInteger(0);
            AtomicInteger totalFailureCount = new AtomicInteger(0);

            for (int i = 0; i < validTokens.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, validTokens.size());
                List<String> batchTokens = validTokens.subList(i, end);

                sendMulticastBatch(batchTokens, title, body, type, roomId, totalSuccessCount, totalFailureCount);
            }

            log.info("FCM 알림 발송 완료 - 성공: {}, 실패: {}, 타입: {}",
                    totalSuccessCount.get(), totalFailureCount.get(), type);

        } catch (Exception e) {
            log.error("FCM 알림 발송 중 예외 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 멀티캐스트 배치 전송 (성능 최적화)
     */
    private void sendMulticastBatch(List<String> tokens, String title, String body, String type, Long roomId,
                                    AtomicInteger totalSuccessCount, AtomicInteger totalFailureCount) {
        try {
            // 멀티캐스트 메시지 생성
            MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .putData("type", type)
                    .putData("title", title)
                    .putData("body", body);

            if (roomId != null) {
                messageBuilder.putData("roomId", roomId.toString());
            }

            MulticastMessage message = messageBuilder.build();

            // 배치 전송 (한 번의 API 호출로 최대 500개 토큰 처리)
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);

            int successCount = response.getSuccessCount();
            int failureCount = response.getFailureCount();

            totalSuccessCount.addAndGet(successCount);
            totalFailureCount.addAndGet(failureCount);

            log.debug("FCM 배치 전송 완료 - 성공: {}, 실패: {}, 배치크기: {}",
                    successCount, failureCount, tokens.size());

            // 실패한 토큰들 처리
            if (failureCount > 0) {
                List<SendResponse> responses = response.getResponses();
                for (int i = 0; i < responses.size(); i++) {
                    SendResponse sendResponse = responses.get(i);
                    if (!sendResponse.isSuccessful()) {
                        String token = tokens.get(i);
                        FirebaseMessagingException exception = sendResponse.getException();

                        if (exception != null &&
                                (exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ||
                                        exception.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT)) {
                            log.info("유효하지 않은 FCM 토큰 제거: {}",
                                    token.substring(0, Math.min(20, token.length())) + "...");
                            fcmTokenService.removeInvalidFcmToken(token);
                        } else {
                            log.warn("FCM 토큰 전송 실패 - Token: {}, Error: {}",
                                    token.substring(0, Math.min(20, token.length())) + "...",
                                    exception != null ? exception.getMessage() : "Unknown error");
                        }
                    }
                }
            }

        } catch (FirebaseMessagingException e) {
            totalFailureCount.addAndGet(tokens.size());

            // 재시도 가능한 오류인지 확인
            if (isRetryableError(e)) {
                log.warn("FCM 배치 전송 재시도 가능한 오류 - 배치크기: {}, Error: {}",
                        tokens.size(), e.getMessage());
                // TODO: 재시도 로직 구현 가능 (현재는 로그만)
            } else {
                log.error("FCM 배치 전송 실패 - 배치크기: {}, Error: {}", tokens.size(), e.getMessage());
            }
        } catch (Exception e) {
            totalFailureCount.addAndGet(tokens.size());
            log.error("FCM 배치 전송 예상치 못한 오류 - 배치크기: {}, Error: {}",
                    tokens.size(), e.getMessage(), e);
        }
    }

    /**
     * 재시도 가능한 Firebase 오류인지 확인
     */
    private boolean isRetryableError(FirebaseMessagingException e) {
        MessagingErrorCode errorCode = e.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.INTERNAL ||
               errorCode == MessagingErrorCode.UNAVAILABLE ||
               (e.getHttpResponse() != null &&
                (e.getHttpResponse().getStatusCode() == 500 ||
                 e.getHttpResponse().getStatusCode() == 503));
    }

    /**
     * FCM 토큰 유효성 검사 (강화된 검증)
     */
    private boolean isValidFcmToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // FCM 토큰 최소 길이 검증 (일반적으로 152자 이상)
        if (token.length() < 140) {
            return false;
        }

        // FCM 토큰은 Base64 URL-safe 문자만 포함
        return token.matches("^[A-Za-z0-9_-]+$");
    }
}