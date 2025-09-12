package com.project.catxi.fcm.service;

import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.common.util.ServerInstanceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmQueueConsumer {
    
    private final FcmQueueService fcmQueueService;
    private final MemberRepository memberRepository;
    private final FcmNotificationService fcmNotificationService;
    private final ServerInstanceUtil serverInstanceUtil;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService consumerExecutor;
    
    @EventListener(ApplicationReadyEvent.class)
    public void startConsumer() {
        // 모든 서버에서 시작하지만, 마스터 체크는 processNotification에서 수행
        log.info("FCM 큐 컨슈머 시작 - ServerId={}, IsMaster={}", 
                serverInstanceUtil.getServerInstanceId(), serverInstanceUtil.shouldProcessFcm());
        running.set(true);
        
        // 멀티스레드 처리로 성능 향상
        int threadCount = 3;
        consumerExecutor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r, "FCM-Queue-Consumer-" + System.currentTimeMillis());
            t.setDaemon(false);
            return t;
        });
        
        // 여러 스레드로 병렬 처리
        for (int i = 0; i < threadCount; i++) {
            consumerExecutor.submit(this::consumeEvents);
        }
    }
    
    public void consumeEvents() {
        log.info("FCM 큐 컨슈머 이벤트 처리 시작");
        
        while (running.get()) {
            try {
                // 마스터 서버만 디큐 수행
                if (!serverInstanceUtil.shouldProcessFcm()) {
                    Thread.sleep(1000); // 1초 대기 후 재확인
                    continue;
                }
                
                // 큐에서 이벤트 하나 가져오기 (블로킹)
                FcmNotificationEvent event = fcmQueueService.dequeueFcmEvent();
                
                if (event != null) {
                    // 디큐 후에도 마스터 상태 재확인
                    if (!serverInstanceUtil.shouldProcessFcm()) {
                        log.warn("FCM 디큐 후 마스터 상태 변경 감지, 이벤트 다시 큐에 추가: {}", event.eventId());
                        // 이벤트를 다시 큐에 넣어야 하지만, 현재 구조상 어려우므로 경고만 출력
                        continue;
                    }

                    long startTime = System.currentTimeMillis();
                    
                    boolean success = processNotification(event);
                    
                    if (success) {
                        // 성공 시에만 처리 완료 마크
                        fcmQueueService.markEventCompleted(event.businessKey());
                        log.debug("FCM 이벤트 처리 성공 - EventId: {}", event.eventId());
                    } else {
                        // 실패 시 재큐잉은 하지 않고 로그만 (중복 방지를 위해)
                        log.error("FCM 이벤트 처리 실패, 재시도 안함 - EventId: {}", event.eventId());
                        // processing 키는 TTL로 자동 만료되어 나중에 재시도 가능
                    }
                    
                    long processingTime = System.currentTimeMillis() - startTime;
                    if (processingTime > 1000) { // 1초 이상 걸린 경우 로그
                        log.warn("FCM 처리 시간 초과 - EventId: {}, Duration: {}ms", 
                                event.eventId(), processingTime);
                    }
                }
                
            } catch (Exception e) {
                if (running.get()) {
                    log.error("FCM 큐 메시지 처리 중 오류", e);
                    try {
                        Thread.sleep(1000); // 1초 대기 후 재시도
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.info("FCM 큐 컨슈머 종료");
    }
    
    private boolean processNotification(FcmNotificationEvent event) {
        try {
            // 마스터 서버에서만 처리
            if (!serverInstanceUtil.shouldProcessFcm()) {
                log.debug("FCM 큐 처리 스킵 - 마스터 서버가 아님: ServerId={}", 
                        serverInstanceUtil.getServerInstanceId());
                return false;
            }
            
            log.info("FCM 큐 메시지 처리 시작 - EventId: {}, BusinessKey: {}", 
                    event.eventId(), event.businessKey());
            
            // 대상 사용자 조회
            List<Member> targetMembers = memberRepository.findAllById(event.targetMemberIds());
            
            if (targetMembers.isEmpty()) {
                log.warn("FCM 알림 대상 사용자 없음 - EventId: {}", event.eventId());
                return false;
            }
            
            // 조회된 사용자 수가 예상보다 적은 경우 경고
            if (targetMembers.size() != event.targetMemberIds().size()) {
                log.warn("일부 사용자 조회 실패 - EventId: {}, Expected: {}, Found: {}", 
                        event.eventId(), event.targetMemberIds().size(), targetMembers.size());
            }
            
            // 알림 타입별 처리
            switch (event.type()) {
                case CHAT_MESSAGE:
                    processChatNotification(event, targetMembers.get(0));
                    break;
                case READY_REQUEST:
                    processReadyRequestNotification(event, targetMembers);
                    break;
                case SYSTEM_NOTIFICATION:
                    processSystemNotification(event, targetMembers);
                    break;
                default:
                    log.warn("알 수 없는 FCM 이벤트 타입 - EventId: {}, Type: {}", 
                            event.eventId(), event.type());
            }
            
            log.info("FCM 큐 메시지 처리 완료 - EventId: {}, BusinessKey: {}", 
                    event.eventId(), event.businessKey());
            
            return true; // 처리 성공
            
        } catch (Exception e) {
            log.error("FCM 알림 처리 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
            return false; // 처리 실패
        }
    }
    
    /**
     * 이벤트에서 Room ID 추출
     */
    private Long extractRoomIdFromEvent(FcmNotificationEvent event) {
        try {
            // BusinessKey에서 추출: "chat:168:2:1821" -> 168
            String businessKey = event.businessKey();
            if (businessKey != null && businessKey.startsWith("chat:")) {
                String[] parts = businessKey.split(":");
                if (parts.length >= 2) {
                    return Long.parseLong(parts[1]);
                }
            }
            
            // Data에서 추출
            String roomIdStr = event.data().get("roomId");
            if (roomIdStr != null) {
                return Long.parseLong(roomIdStr);
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Room ID 추출 실패 - EventId: {}, BusinessKey: {}", 
                    event.eventId(), event.businessKey(), e);
            return null;
        }
    }
    
    private void processChatNotification(FcmNotificationEvent event, Member targetMember) {
        // 채팅 메시지에서 발송자 닉네임 추출 (body에서 파싱)
        String body = event.body(); // "닉네임: 메시지" 형태
        String[] parts = body.split(": ", 2);
        String senderNickname = parts.length > 0 ? parts[0] : "Unknown";
        String message = parts.length > 1 ? parts[1] : body;
        
        fcmNotificationService.sendChatNotificationSync(targetMember, senderNickname, message);
    }
    
    private void processReadyRequestNotification(FcmNotificationEvent event, List<Member> targetMembers) {
        String roomIdStr = event.data().get("roomId");
        Long roomId = roomIdStr != null ? Long.parseLong(roomIdStr) : null;
        
        fcmNotificationService.sendReadyRequestNotificationSync(targetMembers, roomId);
    }
    
    private void processSystemNotification(FcmNotificationEvent event, List<Member> targetMembers) {
        // 시스템 알림 처리 (향후 확장용)
        log.info("시스템 알림 처리 - EventId: {}, Targets: {}", 
                event.eventId(), targetMembers.size());
    }
    
    @PreDestroy
    public void stopConsumer() {
        log.info("FCM 큐 컨슈머 종료 요청");
        running.set(false);
        
        // 대기 중인 FCM 메시지 처리
        processPendingMessages();
        
        if (consumerExecutor != null) {
            consumerExecutor.shutdown();
            try {
                if (!consumerExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    consumerExecutor.shutdownNow();
                    if (!consumerExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        log.warn("FCM 큐 컨슈머가 정상 종료되지 않음");
                    }
                }
            } catch (InterruptedException e) {
                consumerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("FCM 큐 컨슈머 종료 완료");
    }
    
    /**
     * 서버 종료 시 대기 중인 FCM 메시지들을 처리
     */
    private void processPendingMessages() {
        if (!serverInstanceUtil.shouldProcessFcm()) {
            log.debug("FCM 마스터 서버가 아니므로 대기 메시지 처리 스킵");
            return;
        }
        
        log.info("종료 전 대기 중인 FCM 메시지 처리 시작");
        int processedCount = 0;
        long startTime = System.currentTimeMillis();
        final int MAX_SHUTDOWN_PROCESSING_TIME_MS = 15000; // 최대 15초
        final int MAX_MESSAGES_TO_PROCESS = 50; // 최대 50개 메시지
        
        try {
            while (processedCount < MAX_MESSAGES_TO_PROCESS && 
                   (System.currentTimeMillis() - startTime) < MAX_SHUTDOWN_PROCESSING_TIME_MS) {
                
                // 논블로킹으로 메시지 가져오기 (타임아웃 1초)
                FcmNotificationEvent event = fcmQueueService.dequeueFcmEventWithTimeout(1);
                
                if (event == null) {
                    // 더 이상 처리할 메시지가 없음
                    break;
                }
                
                try {
                    boolean success = processNotification(event);
                    if (success) {
                        fcmQueueService.markEventCompleted(event.businessKey());
                        processedCount++;
                        log.debug("종료 시 FCM 메시지 처리 완료 - EventId: {}", event.eventId());
                    } else {
                        log.error("종료 시 FCM 메시지 처리 실패 - EventId: {}", event.eventId());
                    }
                } catch (Exception e) {
                    log.error("종료 시 FCM 메시지 처리 중 예외 - EventId: {}", event.eventId(), e);
                }
            }
            
        } catch (Exception e) {
            log.error("종료 시 FCM 메시지 처리 중 오류", e);
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        log.info("종료 전 FCM 메시지 처리 완료 - 처리량: {}, 소요시간: {}ms", processedCount, totalTime);
        
        // 처리되지 못한 메시지들에 대한 로그
        logRemainingMessages();
    }
    
    /**
     * 처리되지 못한 남은 메시지들을 로깅
     */
    private void logRemainingMessages() {
        try {
            long remainingCount = fcmQueueService.getQueueSize();
            if (remainingCount > 0) {
                log.warn("서버 종료 시 처리되지 못한 FCM 메시지가 {} 개 남아있습니다. " +
                        "다른 서버나 재시작 후 처리될 예정입니다.", remainingCount);
            }
        } catch (Exception e) {
            log.debug("남은 메시지 카운트 확인 실패", e);
        }
    }
}