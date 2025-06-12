package com.project.catxi.chat.dto;

import com.project.catxi.common.domain.MessageType;

public record ChatMessageSendReq(
	Long roomId,
	String email,
	String message,
	MessageType msgType
) {
	public ChatMessageSendReq withRoomId(Long newRoomId) {
		return new ChatMessageSendReq(newRoomId, this.email, this.message,this.msgType);
	}
}