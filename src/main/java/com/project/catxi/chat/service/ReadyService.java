package com.project.catxi.chat.service;


import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReadyService {
	private final ChatParticipantRepository chatParticipantRepository;
	private final MemberRepository memberRepository;
	private final ChatRoomRepository chatRoomRepository;
	private final TimerService timerService;
	private final SseService sseService;
	private final SseSubscriber sseSubscriber;

	@Transactional
	public void requestReady(String roomId, String email) {
		ChatRoom room = chatRoomRepository.findById(Long.valueOf(roomId))
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		if (!participant.isHost()) {
			throw new CatxiException(ChatRoomErrorCode.NOT_OWNED_CHATROOM);
		}

		if (!room.getStatus().equals(RoomStatus.WAITING)) {
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_WAITING);
		}

		room.setStatus(RoomStatus.READY_LOCKED);
		SseSendReq payload = new SseSendReq(
			"READY REQUEST",
			"방장이 Ready 요청을 보냈습니다",
			roomId,
			member.getMembername(),
			"CLIENT"
		);
		sseSubscriber.publish("sse:" + roomId, payload); // publish 호출

		/*
		레디 요청 10초 이후 (레디 요청 당시의 참여자 수 == 10초 이후 참여자 수)가
			- True라면 MATCHED로 변경
			- False라면 WAITING으로 변경
		*/

		timerService.scheduleReadyTimeout(roomId);


	}

	@Transactional
	public void acceptReady(String roomId, String email) {
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(Long.valueOf(roomId))
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		checkParticipant(room,participant);

		participant.setReady(true);
		SseSendReq payload = new SseSendReq(
			"READY ACCEPT",
			"참여자가 Ready를 수락했습니다",
			roomId,
			member.getMembername(),
			"HOST"
		);
		sseSubscriber.publish("sse:" + roomId, payload);

	}

	@Transactional
	public void rejectReady(String roomId, String email) {
		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(Long.valueOf(roomId))
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatParticipant participant = chatParticipantRepository.findByChatRoomAndMember(room, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));

		checkParticipant(room,participant);

		SseSendReq payload = new SseSendReq(
			"READY REJECT",
			"참여자가 Ready를 거절했습니다",
			roomId,
			member.getMembername(),
			"HOST"
		);

		/*
		 * 방장에게 Ready 거절 메시지 전송
		 * sse 연결 해제
		 */
		sseSubscriber.publish("sse:" + roomId, payload);
		sseService.disconnect(roomId, email, false);

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