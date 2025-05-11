package com.project.catxi.chat.dto;

public record ChatMessageSendReq(
	Long   roomId,
	Long   senderId,      // Member PK (or 이메일·닉네임으로 변경 가능)
	String message
) { }
