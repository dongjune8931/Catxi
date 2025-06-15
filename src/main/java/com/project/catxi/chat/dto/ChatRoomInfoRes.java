package com.project.catxi.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;

public record ChatRoomInfoRes(
	Long currentSize, // 현재 참가자수
	Long recruitSize, // 모집 인원수
	RoomStatus roomStatus, // 방상태
	String hostEmail, // hostEmail
	String hostNickname, // hostNickname
	List<String> participantEmails, // 참가자들 email
	List<String> participantNicknames, // 참가자들Nickname
	Location startPoint, // startPoint
	Location endPoint, // endPioint
	LocalDateTime departAt // departAt
) {
	public static ChatRoomInfoRes from(ChatRoom chatRoom, List<String> participantEmails, List<String> participantNicknames) {
		return new ChatRoomInfoRes(
			(long) chatRoom.getParticipants().size(), // 현재 참가자수
			chatRoom.getMaxCapacity(), // 모집 인원수
			chatRoom.getStatus(), // 방상태
			chatRoom.getHost().getEmail(), // hostEmail
			chatRoom.getHost().getNickname(), // hostNickname
			participantEmails, // 참가자들 email
			participantNicknames, // 참가자들Nickname
			chatRoom.getStartPoint(), // startPoint
			chatRoom.getEndPoint(), // endPioint
			chatRoom.getDepartAt() // departAt
		);
	}
}
