package com.example.catxi.taxiParticipant.entity;

import com.example.catxi.member.entity.Member;
import com.example.catxi.taxiParticipant.ParticipantStatus;
import com.example.catxi.taxiRoom.entity.TaxiRoom;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
public class TaxiParticipant {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@ManyToOne(optional = false)
	@JoinColumn(name = "taxi_room_id")
	private TaxiRoom taxiRoom;

	@Enumerated(EnumType.STRING)
	private ParticipantStatus status;


}