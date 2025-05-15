package com.project.catxi.chat.dto;

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
){ }
