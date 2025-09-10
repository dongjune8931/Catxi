package com.project.catxi.chat.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import com.project.catxi.fcm.service.FcmActiveStatusService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompEventListener {
	private final Set<String> sessions = ConcurrentHashMap.newKeySet();
	private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>(); // sessionId -> email
	private final Map<String, Long> sessionRoomMap = new ConcurrentHashMap<>(); // sessionId -> roomId
	private final FcmActiveStatusService fcmActiveStatusService;

	@EventListener
	public void connectHandle(SessionConnectEvent event){
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = accessor.getSessionId();
		sessions.add(sessionId);
		
		// 사용자 정보 저장 (Principal에서 이메일 추출)
		if (accessor.getUser() != null) {
			String email = accessor.getUser().getName();
			sessionUserMap.put(sessionId, email);
			log.debug("WebSocket 연결 - SessionId: {}, Email: {}", sessionId, email);
		}
		
		System.out.println("connect session Id" + sessionId);
		System.out.println("total session : "+sessions.size());
	}

	@EventListener
	public void disconnectHandle(SessionDisconnectEvent event){
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = accessor.getSessionId();
		
		// 활성 상태 비활성화
		String email = sessionUserMap.get(sessionId);
		Long roomId = sessionRoomMap.get(sessionId);
		
		if (email != null && roomId != null) {
			fcmActiveStatusService.updateUserActiveStatus(email, roomId, false);
			log.debug("WebSocket 해제 시 FCM 활성 상태 비활성화 - Email: {}, RoomId: {}", email, roomId);
		}
		
		// 세션 정보 정리
		sessions.remove(sessionId);
		sessionUserMap.remove(sessionId);
		sessionRoomMap.remove(sessionId);
		
		System.out.println("disconnect session Id" + sessionId);
		System.out.println("total session : "+sessions.size());
	}
	
	@EventListener
	public void subscribeHandle(SessionSubscribeEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = accessor.getSessionId();
		String destination = accessor.getDestination();
		
		// 채팅방 구독 시 활성 상태 설정
		if (destination != null && destination.startsWith("/topic/chat/")) {
			try {
				String roomIdStr = destination.substring("/topic/chat/".length());
				Long roomId = Long.parseLong(roomIdStr);
				String email = sessionUserMap.get(sessionId);
				
				if (email != null) {
					sessionRoomMap.put(sessionId, roomId);
					fcmActiveStatusService.updateUserActiveStatus(email, roomId, true);
					log.debug("채팅방 구독 시 FCM 활성 상태 활성화 - Email: {}, RoomId: {}", email, roomId);
				}
			} catch (NumberFormatException e) {
				log.warn("채팅방 ID 파싱 실패 - Destination: {}", destination);
			}
		}
	}
	
	@EventListener
	public void unsubscribeHandle(SessionUnsubscribeEvent event) {
		StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
		String sessionId = accessor.getSessionId();
		
		// 구독 해제 시 활성 상태 비활성화
		String email = sessionUserMap.get(sessionId);
		Long roomId = sessionRoomMap.get(sessionId);
		
		if (email != null && roomId != null) {
			fcmActiveStatusService.updateUserActiveStatus(email, roomId, false);
			sessionRoomMap.remove(sessionId);
			log.debug("구독 해제 시 FCM 활성 상태 비활성화 - Email: {}, RoomId: {}", email, roomId);
		}
	}
}


