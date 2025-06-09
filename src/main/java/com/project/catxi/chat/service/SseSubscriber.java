package com.project.catxi.chat.service;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.dto.SseSendReq;
import com.project.catxi.common.api.error.SseErrorCode;
import com.project.catxi.common.api.exception.CatxiException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SseSubscriber implements MessageListener {

	private final SseService sseService;
	private final ObjectMapper objectMapper;

	private final RedisTemplate<String, Object> redisTemplate;

	public void publish(String channel, SseSendReq sseSendReq) {
		try {
			String payload = objectMapper.writeValueAsString(sseSendReq);
			redisTemplate.convertAndSend(channel, payload);
		} catch (Exception e) {
			throw new CatxiException(SseErrorCode.SSE_SEND_ERROR);
		}
	}

	@Override
	public void onMessage(Message message, byte[] pattern) {
		//System.out.println("📩 Redis Message received: " + new String(message.getBody()));
		String channel = new String(message.getChannel());
		String payload = new String(message.getBody());

		// sse:{roomId} 채널 구조라고 가정
		String roomId = channel.substring("sse:".length());

		try {
			String jsonString = objectMapper.readValue(payload, String.class);
			SseSendReq sseSendReq = objectMapper.readValue(jsonString, SseSendReq.class);
			if(sseSendReq.direction().equals("HOST")) {
				// 호스트에게 메시지를 전송하는 로직
				sseService.sendToHost(roomId, sseSendReq.senderName(), sseSendReq.eventName(), sseSendReq.data());
			} else if(sseSendReq.direction().equals("CLIENT")) {
				// 클라이언트에게 메시지를 전송하는 로직
				sseService.sendToClients(roomId, sseSendReq.eventName(), sseSendReq.data(), true);
			}
		} catch (Exception e) {
			throw new CatxiException(SseErrorCode.SSE_SEND_ERROR);
		}
	}

}
