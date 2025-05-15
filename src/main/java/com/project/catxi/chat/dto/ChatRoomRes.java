package com.project.catxi.chat.dto;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.common.domain.Location;
import com.project.catxi.common.domain.RoomStatus;

public record ChatRoomRes (
	Long roomId,
	Long hostId,
	String hostName,
	String hostNickname,
	Location startPoint,
	Location endPoint,
	Long recruitSize,
	Long currentSize,
	RoomStatus status,
	String departAt,
	String createdTime
){
	public static ChatRoomRes from (ChatRoom chatRoom){
		return new ChatRoomRes(
			chatRoom.getRoomId(),
			chatRoom.getHost().getId(),
			chatRoom.getHost().getName(),
			chatRoom.getHost().getNickname(),
			chatRoom.getStartPoint(),
			chatRoom.getEndPoint(),
			chatRoom.getMaxCapacity(),
			(long)chatRoom.getParticipants().size(),
			chatRoom.getStatus(),
			chatRoom.getDepartAt().toString(),
			chatRoom.getCreatedTime().toString()
		);
	}
}
