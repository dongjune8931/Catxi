package com.project.catxi.chat.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.catxi.chat.service.ChatMessageService;

@RestController
@RequestMapping("/chat")
public class ChatMessageController {

	private final ChatMessageService chatMessageService;

	public ChatMessageController(ChatMessageService chatMessageService) {
		this.chatMessageService = chatMessageService;
	}


}
