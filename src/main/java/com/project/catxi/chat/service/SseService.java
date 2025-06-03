package com.project.catxi.chat.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.catxi.chat.dto.SseSendRes;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.SseErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

@Service
public class SseService {
	// thread-safe 한 컬렉션으로 sse emiiter 객체 관리
	private final Map<String, Map<String, SseEmitter>> sseEmitters = new ConcurrentHashMap<>();
	private final Map<String, SseEmitter> hostEmitters = new ConcurrentHashMap<>();
	private static final Long TIMEOUT = 30 * 60 * 1000L; // 30분

	/*
	 * sse 구독 기능
	 */
	public SseEmitter subscribe(String roomId, String userId, boolean isHost) {
		// 30분 동안 클라이언트와 연결 유지
		SseEmitter emitter = new SseEmitter(TIMEOUT);
		Map<String, SseEmitter> roomEmitters = sseEmitters.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());

		if(isHost){
			// 방장일 경우 hostEmitters에 저장
			hostEmitters.put(roomId, emitter);
			emitter.onCompletion(() -> {hostEmitters.remove(roomId);});
			emitter.onTimeout(() -> {hostEmitters.remove(roomId);});
			emitter.onError((e) -> {hostEmitters.remove(roomId);});
		}
		else{
			// 방장이 아닐 경우 sseEmitters에 저장
			roomEmitters.put(userId, emitter);
			emitter.onCompletion(() -> {roomEmitters.remove(userId);});
			emitter.onTimeout(() -> {roomEmitters.remove(userId);});
			emitter.onError((e) -> {roomEmitters.remove(userId);});
		}

		sendToClient(roomId, "SERVER", emitter,"connected", "SSE connection completed", isHost);

		return emitter;
	}

	/*
	 * 채팅방 전체에게 sse 메시지 전송
	 */
	public void sendToClients(String roomId, String eventName, Object data) {
		Map<String, SseEmitter> sseEmitterList = sseEmitters.get(roomId);

		if(sseEmitterList != null && !sseEmitterList.isEmpty()){
			// 채팅방에 연결된 모든 클라이언트에게 메시지 전송
			for (Map.Entry<String, SseEmitter> entry : sseEmitterList.entrySet()) {
				SseEmitter sseEmitter = entry.getValue();
				sendToClient(roomId, "HOST", sseEmitter, eventName, data, false);
			}
		} else {
			throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
		}
	}

	/*
	 * 방장에게 sse 메시지 전송
	 */
	public void sendToHost(String roomId, String senderName,String eventName, Object data) {
		SseEmitter sseEmitter = hostEmitters.get(roomId);

		if (sseEmitter == null) {
			throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
		}

		sendToClient(roomId, senderName, sseEmitter, eventName, data, true);
	}


	public void sendToClient(String roomId, String senderName,  SseEmitter sseEmitter, String eventName, Object data, boolean isHost) {
		SseSendRes sseSendRes = new SseSendRes(senderName, data, java.time.LocalDateTime.now());

		try {
			sseEmitter.send(SseEmitter.event()
				.name(eventName)
				.data(sseSendRes));
		} catch (Exception e) {
			if (isHost) {
				hostEmitters.remove(roomId);
			} else {
				Map<String, SseEmitter> roomEmitters = sseEmitters.get(roomId);
				if (roomEmitters != null) {
					roomEmitters.values().remove(sseEmitter);
				}
			}
			throw new CatxiException(SseErrorCode.SERVER_SSE_ERROR);

		}
	}

}
