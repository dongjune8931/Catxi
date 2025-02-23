package com.example.catxi.taxiParticipant.dto;

import com.example.catxi.member.entity.Member;
import com.example.catxi.taxiParticipant.ParticipantStatus;
import com.example.catxi.taxiRoom.entity.TaxiRoom;

public record TaxiParticipantDto(
	Long id,
	Member member,
	TaxiRoom taxiRoom,
	ParticipantStatus status
) {}