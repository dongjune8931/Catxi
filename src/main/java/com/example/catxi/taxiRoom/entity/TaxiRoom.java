package com.example.catxi.taxiRoom.entity;

import java.time.LocalDateTime;

import com.example.catxi.taxiRoom.RoomStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TaxiRoom {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String departure;
	private String destination;
	private LocalDateTime departureTime;

	@Enumerated(EnumType.STRING)
	private RoomStatus status;

	private String meetingAddress;

	private Double latitude;
	private Double longitude;

}