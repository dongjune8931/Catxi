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

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmQueueConsumer {
    
    private final FcmQueueService fcmQueueService;
    private final MemberRepository memberRepository;
    private final FcmNotificationService fcmNotificationService;
    private final ServerInstanceUtil serverInstanceUtil;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @EventListener(ApplicationReadyEvent.class)
    public void startConsumer() {
        log.info("FCM 큐 컨슈머 시작");
        running.set(true);
        
        // 별도 스레드에서 실행
        Thread consumerThread = new Thread(this::consumeEvents, "FCM-Queue-Consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }
    
    public void consumeEvents() {
        log.info("FCM 큐 컨슈머 이벤트 처리 시작");
        
        while (running.get()) {
            try {
                // 큐에서 이벤트 하나 가져오기 (블로킹)
                FcmNotificationEvent event = fcmQueueService.dequeueFcmEvent();
                
                if (event != null) {
                    long startTime = System.currentTimeMillis();
                    
                    processNotification(event);
                    
                    // 처리 완료 마크 (비동기)
                    fcmQueueService.markEventCompleted(event.businessKey());
                    
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
    
    private void processNotification(FcmNotificationEvent event) {
        try {
            log.info("FCM 큐 메시지 처리 시작 - EventId: {}, BusinessKey: {}", 
                    event.eventId(), event.businessKey());
            
            // Room ID 추출하여 현재 서버가 처리할지 확인
            Long roomId = extractRoomIdFromEvent(event);
            if (roomId != null && !serverInstanceUtil.shouldProcessFcmForRoom(roomId)) {
                log.debug("FCM 처리 스킵 - 다른 서버에서 처리: RoomId={}, EventId={}, ServerId={}", 
                        roomId, event.eventId(), serverInstanceUtil.getServerInstanceId());
                return;
            }
            
            log.debug("FCM 처리 시작 - 현재 서버에서 처리: RoomId={}, EventId={}, ServerId={}", 
                    roomId, event.eventId(), serverInstanceUtil.getServerInstanceId());
            
            // 대상 사용자 조회
            List<Member> targetMembers = memberRepository.findAllById(event.targetMemberIds());
            
            if (targetMembers.isEmpty()) {
                log.warn("FCM 알림 대상 사용자 없음 - EventId: {}", event.eventId());
                return;
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
            
        } catch (Exception e) {
            log.error("FCM 알림 처리 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
            throw new RuntimeException(e);
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
        running.set(false);
        log.info("FCM 큐 컨슈머 종료 요청");
    }
}