package com.project.catxi.chat.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.dto.ReadyMessageRes;
import com.project.catxi.map.dto.CoordinateReq;

@Service
public class RedisPubSubService implements MessageListener {
	private final SimpMessageSendingOperations messageTemplate;
	public final StringRedisTemplate stringRedisTemplate;

	public RedisPubSubService(@Qualifier("chatPubSub") StringRedisTemplate stringRedisTemplate,
		SimpMessageSendingOperations messageTemplate) {
		this.messageTemplate = messageTemplate;
		this.stringRedisTemplate = stringRedisTemplate;
	}

	public void publish(String channel, String message) {
		stringRedisTemplate.convertAndSend(channel, message);
	}

	@Override
	//pattern 에는 topic의 이름의 패턴이 담겨있고 이 패턴을 기반으로 다이나믹한 코딩
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(pattern);
		String payload = new String(message.getBody());
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		try {
			if ("chat".equals(channel)) {
			ChatMessageSendReq chatMessageDto = objectMapper.readValue(payload, ChatMessageSendReq.class);
				messageTemplate.convertAndSend("/topic/" + chatMessageDto.roomId(), chatMessageDto);
			} else if (channel.startsWith("ready:")) {
				ReadyMessageRes readyMessage = objectMapper.readValue(payload, ReadyMessageRes.class);
				// ready 메시지는 별도의 토픽으로 보낼 수 있음 (예: /topic/ready/{roomId})
				messageTemplate.convertAndSend("/topic/ready/" + readyMessage.roomId(), readyMessage);
			} else if (channel.startsWith("map:")) {
				// 지도 좌표 관련 메시지 처리
				CoordinateReq coordinateReq = objectMapper.readValue(payload, CoordinateReq.class);
				messageTemplate.convertAndSend("/topic/map/" + coordinateReq.roomId(), coordinateReq);
			}
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
