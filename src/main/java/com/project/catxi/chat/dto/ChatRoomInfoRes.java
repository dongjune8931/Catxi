package com.project.catxi.chat.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;

public record ChatRoomInfoRes(
	Long currentSize,
	Long recruitSize,
	RoomStatus roomStatus,
	String hostEmail,
	String hostNickname,
	List<String> participantEmails,
	List<String> participantNicknames,
	Location startPoint,
	Location endPoint,
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Seoul")
	LocalDateTime departAt
) {
	public static ChatRoomInfoRes from(ChatRoom chatRoom, List<ParticipantInfo> participants) {
		return new ChatRoomInfoRes(
			(long) chatRoom.getParticipants().size(),
			chatRoom.getMaxCapacity(),
			chatRoom.getStatus(),
			chatRoom.getHost().getEmail(),
			chatRoom.getHost().getNickname(),
			participants.stream().map(ParticipantInfo::getEmail).collect(Collectors.toList()),
			participants.stream().map(ParticipantInfo::getNickname).collect(Collectors.toList()),
			chatRoom.getStartPoint(),
			chatRoom.getEndPoint(),
			chatRoom.getDepartAt()
		);
	}
}
