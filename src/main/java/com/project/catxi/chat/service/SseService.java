package com.project.catxi.chat.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

@Service
public class SseService {
	// thread-safe 한 컬렉션으로 sse emiiter 객체 관리
	private final Map<String, CopyOnWriteArrayList<SseEmitter>> sseEmitters = new ConcurrentHashMap<>();
	private final Map<String, SseEmitter> hostEmitters = new ConcurrentHashMap<>();
	private static final Long TIMEOUT = 30 * 60 * 1000L; // 30분

	/*
	 * sse 구독 기능
	 */
	public SseEmitter subscribe(String id) {
		// 30분 동안 클라이언트와 연결 유지
		SseEmitter emitter = new SseEmitter(TIMEOUT);

		sseEmitters.computeIfAbsent(id, key -> new CopyOnWriteArrayList<>());
		sseEmitters.get(id).add(emitter);

		// 클라이언트 연결 종료, 타임아웃, 에러 발생 시 emitter 제거
		emitter.onCompletion(() -> {
			sseEmitters.get(id).remove(emitter);
		});
		emitter.onTimeout(() -> {
			sseEmitters.get(id).remove(emitter);
		});
		emitter.onError((e) -> {
			sseEmitters.get(id).remove(emitter);
		});

		// 연결 성공 시 클라이언트에게 메시지 전송
		sendToClient(id, emitter,"connected", "SSE connection completed");

		return emitter;
	}

	/*
	 * 채팅방 전체에게 sse 메시지 전송
	 */
	public void sendToClients(String roomId, String eventName, Object data) {
		CopyOnWriteArrayList<SseEmitter> sseEmitterList = sseEmitters.get(roomId);

		if(sseEmitterList != null && !sseEmitterList.isEmpty()){
			// 채팅방에 연결된 모든 클라이언트에게 메시지 전송
			for (SseEmitter sseEmitter : sseEmitterList) {
				sendToClient(roomId, sseEmitter, eventName, data);
			}
		} else {
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND);
		}
	}

	/*
	 * 방장에게 sse 메시지 전송
	 */
	/*public void sendToHost(String roomId, String eventName, Object data) {
		SseEmitter sseEmitter = hostEmitters.get(roomId);

		if (sseEmitter == null) {
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND);
		}

		sendToClient(roomId, sseEmitter, eventName, data);
	}*/

	public void sendToClient(String roomId, SseEmitter sseEmitter, String eventName, Object data) {
		try {
			sseEmitter.send(SseEmitter.event()
				.name(eventName)
				.data(data));
		} catch (Exception e) {
			sseEmitters.get(roomId).remove(sseEmitter); // 전송 실패 시 emitter 제거
		}
	}
}
