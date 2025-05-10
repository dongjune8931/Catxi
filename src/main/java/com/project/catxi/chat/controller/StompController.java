package com.project.catxi.chat.controller;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.catxi.chat.dto.ChatMessageReqDto;

@Controller
public class StompController {

	private final SimpMessageSendingOperations messageTemplate;

	public StompController(SimpMessageSendingOperations messageTemplate) {
		this.messageTemplate = messageTemplate;
	}

	@MessageMapping("/{roomId}")
	public void sendMessage(@DestinationVariable Long roomId, ChatMessageReqDto chatMessageReqDto) {
		System.out.println(chatMessageReqDto.getMessage());
		//해당 roomId에 메시지를 방행하여 구독중인 클라이언트에게 메시지 전송
		messageTemplate.convertAndSend("/topic/" + roomId, chatMessageReqDto);
	}
}
