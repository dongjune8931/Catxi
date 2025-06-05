package com.project.catxi.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.catxi.chat.dto.SseSendRes;
import com.project.catxi.common.api.error.SseErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

@Service
public class SseService {
	// thread-safe 한 컬렉션으로 sse emiiter 객체 관리
	private final Map<String, Map<String, SseEmitter>> sseEmitters = new ConcurrentHashMap<>();
	private final Map<String, SseEmitter> hostEmitters = new ConcurrentHashMap<>();
	private final Map<String, Long> roomReadyTime = new ConcurrentHashMap<>();
	private static final Long TIMEOUT = 30 * 60 * 1000L; // 30분

	/*
	 * sse 구독 기능
	 */
	public SseEmitter subscribe(String roomId, String membername, boolean isHost) {
		// 30분 동안 클라이언트와 연결 유지
		SseEmitter emitter = new SseEmitter(TIMEOUT);
		Map<String, SseEmitter> roomEmitters = sseEmitters.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

		if (isRoomBlocked(roomId)) {
			throw new CatxiException(SseErrorCode.SSE_ROOM_BLOCKED);
		}

		if (isHost) {
			registerEmitter(hostEmitters, roomId, emitter);
		} else {
			registerEmitter(roomEmitters, membername, emitter);
		}

		try {
			sendToClient("SERVER", emitter, "connected", "SSE connection completed");
		} catch (Exception e) {
			disconnect(roomId, membername, isHost);
		}

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
			throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
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
			throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
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
	public void disconnect(String roomId, String membername, boolean isHost) {
		if (isHost) {
			deleteEmitter(hostEmitters, roomId);
		} else {
			Map<String, SseEmitter> roomEmitters = sseEmitters.get(roomId);

			if (roomEmitters == null) {
				throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
			}
			deleteEmitter(roomEmitters, membername);

			if (roomEmitters.isEmpty()) {
				sseEmitters.remove(roomId);
			}
		}
	}


	private void sendToClient(String senderName,  SseEmitter sseEmitter, String eventName, String message) {
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
			existing.complete();
		}

		emitterMap.put(key, emitter);

		emitter.onCompletion(() -> emitterMap.remove(key));
		emitter.onTimeout(() -> emitterMap.remove(key));
		emitter.onError((e) -> emitterMap.remove(key));
	}

	private void deleteEmitter(Map<String, SseEmitter> emitterMap, String key) {
		SseEmitter emitter = emitterMap.get(key);
		if (emitter != null) {
			emitter.complete();
			emitterMap.remove(key);
		}
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


}
