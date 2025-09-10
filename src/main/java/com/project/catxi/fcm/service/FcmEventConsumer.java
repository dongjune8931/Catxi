package com.project.catxi.fcm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.fcm.dto.FcmNotificationEvent;
import com.project.catxi.fcm.util.FcmBusinessKeyGenerator;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.common.service.RedisDistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmEventConsumer implements MessageListener {
    
    private final ObjectMapper objectMapper;
    private final MemberRepository memberRepository;
    private final FcmNotificationService fcmNotificationService;
    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    private final RedisDistributedLockService distributedLockService;
    private final FcmBusinessKeyGenerator businessKeyGenerator;
    
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String eventJson = new String(message.getBody());
            FcmNotificationEvent event = objectMapper.readValue(eventJson, FcmNotificationEvent.class);
            
            // 비즈니스 로직 기반 분산락 키 생성
            String businessLockKey = businessKeyGenerator.generateBusinessKey(event);
            
            log.info("FCM 이벤트 수신 - EventId: {}, Type: {}, BusinessKey: {}", 
                    event.eventId(), event.type(), businessLockKey);
            
            // 분산락을 사용하여 중복 처리 방지 (30초 TTL)
            boolean lockAcquired = distributedLockService.executeWithLock(
                businessLockKey, 
                30, // 30초 락 TTL
                () -> {
                    log.info("FCM 처리 시작 - EventId: {}, BusinessKey: {}", 
                            event.eventId(), businessLockKey);
                    processNotificationAsync(event);
                }
            );
            
            if (!lockAcquired) {
                log.debug("FCM 이벤트 중복 처리 방지 (분산락) - EventId: {}, BusinessKey: {}", 
                        event.eventId(), businessLockKey);
            }
            
        } catch (Exception e) {
            log.error("FCM 이벤트 처리 실패 - Error: {}", e.getMessage(), e);
        }
    }
    
    @Async("fcmTaskExecutor")
    public CompletableFuture<Void> processNotificationAsync(FcmNotificationEvent event) {
        try {
            // 대상 사용자 조회
            List<Member> targetMembers = memberRepository.findAllById(event.targetMemberIds());
            
            if (targetMembers.isEmpty()) {
                log.warn("FCM 알림 대상 사용자 없음 - EventId: {}", event.eventId());
                return CompletableFuture.completedFuture(null);
            }
            
            // 조회된 사용자 수가 예상보다 적은 경우 경고
            if (targetMembers.size() != event.targetMemberIds().size()) {
                log.warn("일부 사용자 조회 실패 - EventId: {}, Expected: {}, Found: {}", 
                        event.eventId(), event.targetMemberIds().size(), targetMembers.size());
            }
            
            // 알림 타입별 처리
            switch (event.type()) {
                case CHAT_MESSAGE:
                    return processChatNotification(event, targetMembers.get(0));
                case READY_REQUEST:
                    return processReadyRequestNotification(event, targetMembers);
                case SYSTEM_NOTIFICATION:
                    return processSystemNotification(event, targetMembers);
                default:
                    log.warn("알 수 없는 FCM 이벤트 타입 - EventId: {}, Type: {}", 
                            event.eventId(), event.type());
                    return CompletableFuture.completedFuture(null);
            }
            
        } catch (Exception e) {
            log.error("FCM 알림 비동기 처리 실패 - EventId: {}, Error: {}", 
                    event.eventId(), e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    private CompletableFuture<Void> processChatNotification(FcmNotificationEvent event, Member targetMember) {
        // 채팅 메시지에서 발송자 닉네임 추출 (body에서 파싱)
        String body = event.body(); // "닉네임: 메시지" 형태
        String[] parts = body.split(": ", 2);
        String senderNickname = parts.length > 0 ? parts[0] : "Unknown";
        String message = parts.length > 1 ? parts[1] : body;
        
        fcmNotificationService.sendChatNotificationSync(targetMember, senderNickname, message);
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> processReadyRequestNotification(FcmNotificationEvent event, List<Member> targetMembers) {
        String roomIdStr = event.data().get("roomId");
        Long roomId = roomIdStr != null ? Long.parseLong(roomIdStr) : null;
        
        fcmNotificationService.sendReadyRequestNotificationSync(targetMembers, roomId);
        return CompletableFuture.completedFuture(null);
    }
    
    private CompletableFuture<Void> processSystemNotification(FcmNotificationEvent event, List<Member> targetMembers) {
        // 시스템 알림 처리 (향후 확장용)
        log.info("시스템 알림 처리 - EventId: {}, Targets: {}", 
                event.eventId(), targetMembers.size());
        return CompletableFuture.completedFuture(null);
    }
}