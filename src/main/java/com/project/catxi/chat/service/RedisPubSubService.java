package com.project.catxi.chat.service;

import java.nio.charset.StandardCharsets;

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
import com.project.catxi.chat.dto.ParticipantsUpdateMessage;
import com.project.catxi.chat.dto.ReadyMessageRes;
import com.project.catxi.map.dto.CoordinateRes;

@Service
public class RedisPubSubService implements MessageListener {
	private final SimpMessageSendingOperations messageTemplate;
	public final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;

	public RedisPubSubService(@Qualifier("chatPubSub") StringRedisTemplate stringRedisTemplate,
		SimpMessageSendingOperations messageTemplate,ObjectMapper objectMapper) {
		this.messageTemplate = messageTemplate;
		this.stringRedisTemplate = stringRedisTemplate;
		this.objectMapper=objectMapper;
	}

	public void publish(String channel, String message) {
		stringRedisTemplate.convertAndSend(channel, message);
	}

	@Override
	//pattern 에는 topic의 이름의 패턴이 담겨있고 이 패턴을 기반으로 다이나믹한 코딩
	public void onMessage(Message message, byte[] pattern) {
		String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
		String payload = new String(message.getBody(),StandardCharsets.UTF_8);

		try {
			if ("chat".equals(channel)) {
			ChatMessageSendReq chatMessageDto = objectMapper.readValue(payload, ChatMessageSendReq.class);
				messageTemplate.convertAndSend("/topic/" + chatMessageDto.roomId(), chatMessageDto);
			} else if (channel.startsWith("ready:")) {
				ReadyMessageRes readyMessage = objectMapper.readValue(payload, ReadyMessageRes.class);
				messageTemplate.convertAndSend("/topic/ready/" + readyMessage.roomId(), readyMessage);
			} else if (channel.equals("map")) {
				CoordinateRes coordinateRes = objectMapper.readValue(payload, CoordinateRes.class);
				messageTemplate.convertAndSend("/topic/map/" + coordinateRes.roomId(), coordinateRes);
			} else if (channel.startsWith("participants:")) {
				ParticipantsUpdateMessage update = objectMapper.readValue(payload, ParticipantsUpdateMessage.class);
				messageTemplate.convertAndSend("/topic/room/" + update.roomId() + "/participants", update);
			} else if (channel.startsWith("kick:")) {
				String email = channel.split(":",2)[1];
				messageTemplate.convertAndSendToUser(email, "/queue/kick", "KICKED");
			}
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
