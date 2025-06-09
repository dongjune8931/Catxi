package com.project.catxi.chat.dto;

public record ChatMessageSendReq(
	Long roomId,
	String email,
	String message
) {
	public ChatMessageSendReq withRoomId(Long newRoomId) {
		return new ChatMessageSendReq(newRoomId, this.email, this.message);
	}
}