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
	private static final Long TIMEOUT = 30 * 60 * 1000L; // 30분

	/*
	 * sse 구독 기능
	 */
	public SseEmitter subscribe(String roomId, String membername, boolean isHost) {
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
			roomEmitters.put(membername, emitter);
			emitter.onCompletion(() -> {roomEmitters.remove(membername);});
			emitter.onTimeout(() -> {roomEmitters.remove(membername);});
			emitter.onError((e) -> {roomEmitters.remove(membername);});
		}

		try {
			sendToClient("SERVER", emitter, "connected", "SSE connection completed");
		} catch (Exception e) {
			if (isHost)
				hostEmitters.remove(roomId);
			else
				roomEmitters.remove(membername);
		}

		return emitter;
	}

	/*
	 * 채팅방 전체에게 sse 메시지 전송
	 */
	public void sendToClients(String roomId, String eventName, String data) {
		Map<String, SseEmitter> sseEmitterList = sseEmitters.get(roomId);

		if (sseEmitterList == null || sseEmitterList.isEmpty()) {
			throw new CatxiException(SseErrorCode.SSE_NOT_FOUND);
		}

		List<String> toRemove = new ArrayList<>();

		for (Map.Entry<String, SseEmitter> entry : sseEmitterList.entrySet()) {
			String userId = entry.getKey();
			SseEmitter emitter = entry.getValue();

			try {
				sendToClient(userId, emitter, eventName, data);
			} catch (Exception e) {
				toRemove.add(userId);
			}
		}

		toRemove.forEach(sseEmitterList::remove);
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

}
