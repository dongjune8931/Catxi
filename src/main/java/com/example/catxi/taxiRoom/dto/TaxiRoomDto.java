package com.example.catxi.taxiRoom.dto;

import java.time.LocalDateTime;

import com.example.catxi.taxiRoom.RoomStatus;

public record TaxiRoomDto(
	Long id,
	String departure,
	String destination,
	LocalDateTime departureTime,
	RoomStatus roomstatus,
	String meetingAddress,
	Double latitude,
	Double longitude
) {}