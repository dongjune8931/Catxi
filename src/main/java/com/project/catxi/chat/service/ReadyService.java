package com.project.catxi.chat.service;


import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.ReadyMessageEvent;
import com.project.catxi.chat.dto.ReadyMessageRes;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.ReadyType;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReadyService {
	private final ApplicationEventPublisher eventPublisher;
	private final ChatParticipantRepository chatParticipantRepository;
	private final MemberRepository memberRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final TimerService timerService;

	@Transactional
	public void requestReady(Long roomId, String email){
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		if (!participant.isHost()) {
			throw new CatxiException(ChatRoomErrorCode.NOT_HOST);
		}

		if (!room.getStatus().equals(RoomStatus.WAITING)) {
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_WAITING);
		}

		room.setStatus(RoomStatus.READY_LOCKED);

		/*
		레디 요청 10초 이후 (레디 요청 당시의 참여자 수 == 10초 이후 참여자 수)가
			- True라면 MATCHED로 변경
			- False라면 WAITING으로 변경
		*/
		ReadyMessageRes payload = ReadyMessageRes.readyRequest(roomId, member);
		eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));

		timerService.scheduleReadyTimeout(roomId.toString());

	}

	@Transactional
	public void acceptReady(Long roomId, String email) {
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		checkParticipant(room,participant);

		participant.setReady(true);

		ReadyMessageRes payload = ReadyMessageRes.readyAccept(roomId, member);
		eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));

	}

	@Transactional
	public void rejectReady(Long roomId, String email) {
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		checkParticipant(room,participant);


		ReadyMessageRes payload = ReadyMessageRes.readyDeny(roomId, member);
		eventPublisher.publishEvent(new ReadyMessageEvent("ready:" + roomId, payload));

	}

	private void checkParticipant(ChatRoom room, ChatParticipant participant) {
		if(!room.getStatus().equals(RoomStatus.READY_LOCKED)) {
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_READY_LOCKED);
		}
		if(participant.isHost()) {
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		}

		if (participant.isReady()) {
			throw new CatxiException(ChatParticipantErrorCode.ALREADY_READY);
		}
	}


}