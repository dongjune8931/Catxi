package com.project.catxi.chat.controller;

import java.time.LocalDateTime;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.service.ChatMessageService;
import com.project.catxi.chat.service.RedisPubSubService;


@Controller
public class StompController {

	private final SimpMessageSendingOperations messageTemplate;
	private final ChatMessageService chatMessageService;
	private final RedisPubSubService pubSubService;
	private final ObjectMapper objectMapper;

	public StompController(SimpMessageSendingOperations messageTemplate, ChatMessageService chatMessageService,
		RedisPubSubService pubSubService,ObjectMapper objectMapper) {
		this.messageTemplate = messageTemplate;
		this.chatMessageService = chatMessageService;
		this.pubSubService = pubSubService;
		this.objectMapper=objectMapper;
	}



	@MessageMapping("/{roomId}")
	public void sendMessage(@DestinationVariable Long roomId, ChatMessageSendReq chatMessageSendReq) throws JsonProcessingException {
		chatMessageService.saveMessage(roomId, chatMessageSendReq);
		//messageTemplate.convertAndSend("/topic/"+ roomId,chatMessageSendReq);

		ChatMessageSendReq enriched = new ChatMessageSendReq(
			chatMessageSendReq.roomId(),
			chatMessageSendReq.email(),
			chatMessageSendReq.message(),
			LocalDateTime.now()
		);
		String message = objectMapper.writeValueAsString(enriched);
		pubSubService.publish("chat", message);
	}
}
