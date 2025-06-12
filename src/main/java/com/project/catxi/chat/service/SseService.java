package com.project.catxi.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.catxi.chat.dto.SseSendRes;
import com.project.catxi.common.api.error.SseErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

import jakarta.annotation.PostConstruct;

@Service
public class SseService {
	private static final Logger log = LoggerFactory.getLogger(SseService.class);
	// thread-safe 한 컬렉션으로 sse emiiter 객체 관리
	private final Map<String, Map<String, SseEmitter>> sseEmitters = new ConcurrentHashMap<>();
	private final Map<String, SseEmitter> hostEmitters = new ConcurrentHashMap<>();
	private final Map<String, Long> roomReadyTime = new ConcurrentHashMap<>();
	private final ScheduledExecutorService pingScheduler = Executors.newSingleThreadScheduledExecutor();
	private final Map<String, SseEmitter> allEmitters = new ConcurrentHashMap<>(); // key: roomId_email 또는 host 키

	private static final Long TIMEOUT = 30 * 60 * 1000L; // 30분


	@PostConstruct
	public void startPingScheduler() {
		pingScheduler.scheduleAtFixedRate(() -> {
			for (Map.Entry<String, SseEmitter> entry : allEmitters.entrySet()) {
				try {
					entry.getValue().send(SseEmitter.event().name("ping").data("ping"));
				} catch (Exception e) {
					log.warn("Ping 실패: key={}, error={}", entry.getKey(), e.getMessage());
					removeEmitter(entry.getKey()); // 실패한 emitter 제거
				}
			}
		}, 0, 60, TimeUnit.SECONDS);
	}

	/*
	 * sse 구독 기능
	 */
	public SseEmitter subscribe(String roomId, String email, boolean isHost, boolean isParticipant) {
		// 30분 동안 클라이언트와 연결 유지
		SseEmitter emitter = new SseEmitter(TIMEOUT);
		Map<String, SseEmitter> roomEmitters = sseEmitters.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

		if (!isParticipant) {
			sendToClient("SERVER", emitter, "error", "채팅방 참여자가 아닙니다");
			emitter.complete();
		}

		if (isRoomBlocked(roomId)) {
			sendToClient("SERVER", emitter, "error", "현재 채팅방에 연결이 불가합니다");
			emitter.complete();
		}

		if (isHost) {
			registerEmitter(hostEmitters, roomId, emitter);
		} else {
			registerEmitter(roomEmitters, email, emitter);
		}

		try {
			sendToClient("SERVER", emitter, "connected", "SSE connection completed");
		} catch (Exception e) {
			disconnect(roomId, email, isHost);
		}

		// emitter 개수 확인 로그
		int hostCount = hostEmitters.containsKey(roomId) ? 1 : 0;
		int clientCount = sseEmitters.getOrDefault(roomId, Map.of()).size();
		log.info("SSE 연결됨 - roomId: {}, hostCount: {}, clientCount: {}, total: {}",
			roomId, hostCount, clientCount, hostCount + clientCount);

		return emitter;
	}

	/*
	 * 채팅방 전체에게 sse 메시지 전송
	 */
	public void sendToClients(String roomId, String eventName, String data, boolean isHost) {
		if(!isHost){
			throw new CatxiException(SseErrorCode.SSE_NOT_HOST);
		}

		Map<String, SseEmitter> sseEmitterList = sseEmitters.get(roomId);

		if (sseEmitterList == null || sseEmitterList.isEmpty()) {
			log.warn("Cannot send SSE message, emitter is null (event: {}, message: {})", eventName, data);
			return;
		}

		roomReadyTime.putIfAbsent(roomId, System.currentTimeMillis());
		List<String> toRemove = new ArrayList<>();

		for (Map.Entry<String, SseEmitter> entry : sseEmitterList.entrySet()) {
			String userId = entry.getKey();
			SseEmitter emitter = entry.getValue();
			try {
				sendToClient("HOST", emitter, eventName, data);
			} catch (Exception e) {
				toRemove.add(userId);
			}
		}

		for (String userId : toRemove) {
			disconnect(roomId, userId, false);
		}
	}

	/*
	 * 방장에게 sse 메시지 전송
	 */
	public void sendToHost(String roomId, String senderName,String eventName, String data) {
		SseEmitter sseEmitter = hostEmitters.get(roomId);

		if (sseEmitter == null) {
			log.warn("Cannot send SSE message, emitter is null (event: {}, message: {})", eventName, data);
			return;
		}

		try {
			sendToClient(senderName, sseEmitter, eventName, data);
		} catch (Exception e) {
			hostEmitters.remove(roomId);
		}
	}

	/*
	 * sse 연결 해제
	 */
	public void disconnect(String roomId, String email, boolean isHost) {
		if (isHost) {
			SseEmitter hostEmitter = hostEmitters.get(roomId);
			if (hostEmitter == null) {
				throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
			}
			deleteEmitter(hostEmitters, roomId);
		} else {
			Map<String, SseEmitter> roomEmitters = sseEmitters.get(roomId);

			if (roomEmitters == null) {
				throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
			}
			deleteEmitter(roomEmitters, email);

			if (roomEmitters.isEmpty()) {
				sseEmitters.remove(roomId);
			}
		}
	}


	private void sendToClient(String senderName,  SseEmitter sseEmitter, String eventName, String message) {
		if (sseEmitter == null) {
			log.warn("Cannot send SSE message, emitter is null (event: {}, message: {})", eventName, message);
			return;
		}

		SseSendRes sseSendRes = new SseSendRes(senderName, message, java.time.LocalDateTime.now());

		try {
			sseEmitter.send(SseEmitter.event()
				.name(eventName)
				.data(sseSendRes));
		} catch (Exception  e) {
			throw new CatxiException(SseErrorCode.SSE_SEND_ERROR);
		}
	}


	private void registerEmitter(Map<String, SseEmitter> emitterMap, String key, SseEmitter emitter) {
		SseEmitter existing = emitterMap.get(key);
		if (existing != null) {
			log.warn("SseEmitter가 이미 존재하여 제거 후 새로 연결합니다 key: {}", key);
			existing.complete();
		}

		if (emitter == null) {
			log.error("SseEmitter가 비어 있어습니다 key: {}", key);
			return;
		}

		emitterMap.put(key, emitter);
		allEmitters.put(key, emitter);

		emitter.onCompletion(() -> {
			log.info("SseEmitter 연결 정리 key: {}", key);
			deleteEmitter(emitterMap, key);
		});
		emitter.onTimeout(() ->{
			log.warn("SseEmitter 연결 타임아웃 key: {}", key);
			deleteEmitter(emitterMap, key);
		});
		emitter.onError((e) -> {
			log.error("SseEmitter 연결 에러 key: {}, error: {}", key, e.getMessage());
			deleteEmitter(emitterMap, key);
		});
	}

	private void deleteEmitter(Map<String, SseEmitter> emitterMap, String key) {
		SseEmitter emitter = emitterMap.get(key);
		if (emitter != null) {
			emitter.complete();
			emitterMap.remove(key);
		}
		allEmitters.remove(key);
	}

	public boolean isRoomBlocked(String roomId) {
		long now = System.currentTimeMillis();
		Long readyTime = roomReadyTime.get(roomId);

		if (readyTime == null) {
			return false;
		}

		long elapsed = now - readyTime;

		if (elapsed < 10 * 1000) {
			return true;
		} else {
			roomReadyTime.remove(roomId);
		}

		return false;
	}

	public SseEmitter createErrorEmitter(String errorMessage) {
		SseEmitter emitter = new SseEmitter(3000L);
		try {
			sendToClient("SERVER", emitter, "error", errorMessage);
		} catch (Exception e) {
			emitter.completeWithError(e);
			return emitter;
		}
		emitter.complete();
		return emitter;
	}

	private void removeEmitter(String key) {
		SseEmitter emitter = allEmitters.remove(key);
		if (emitter != null) {
			emitter.complete();
		}
	}

}
