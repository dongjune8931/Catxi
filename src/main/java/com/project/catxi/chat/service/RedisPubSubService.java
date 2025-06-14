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
		String payload = new String(message.getBody());
		ObjectMapper objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		try {
			ChatMessageSendReq chatMessageDto = objectMapper.readValue(payload, ChatMessageSendReq.class);
			messageTemplate.convertAndSend("/topic/" + chatMessageDto.roomId(), chatMessageDto);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
