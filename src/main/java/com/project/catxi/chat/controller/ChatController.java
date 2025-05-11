package com.project.catxi.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.catxi.chat.service.ChatMessageService;
import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.api.CommonPageResponse;

@RestController
@RequestMapping("/chat")
public class ChatController {

	private final ChatMessageService chatMessageService;

	public ChatController(ChatMessageService chatMessageService) {
		this.chatMessageService = chatMessageService;
	}

}
