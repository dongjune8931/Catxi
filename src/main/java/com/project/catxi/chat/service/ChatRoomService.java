package com.project.catxi.chat.service;


import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.RoomCreateReq;
import com.project.catxi.chat.dto.RoomCreateRes;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {


	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final MemberRepository memberRepository;


	private void HostNotInOtherRoom(Member host) {
		boolean exists = chatParticipantRepository.existsByMemberAndActiveTrue(host);
		if (exists)
			throw new CatxiException(ChatParticipantErrorCode.ALREADY_IN_ACTIVE_ROOM);
	}



	public RoomCreateRes creatRoom(RoomCreateReq roomReq, Member host){
		HostNotInOtherRoom(host);
		if(roomReq.startPoint().equals(roomReq.endPoint()))
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		ChatRoom room = ChatRoom.builder()
			.host(host)
			.startPoint(roomReq.startPoint())
			.endPoint(roomReq.endPoint())
			.departAt(roomReq.departAt())
			.status(RoomStatus.WAITING)
			.maxCapacity(roomReq.recruitSize())
			.build();
		chatRoomRepository.save(room);

		ChatParticipant hostPart = ChatParticipant.builder()
			.chatRoom(room)
			.member(host)
			.isHost(true)
			.isReady(true)
			.isActive(true)
			.build();
		chatParticipantRepository.save(hostPart);

		return new RoomCreateRes(
			room.getRoomId(),
			room.getStartPoint(),
			room.getEndPoint(),
			room.getMaxCapacity(),
			room.getDepartAt(),
			room.getStatus()
		);
	}


	//로그인 전이라 member 임시로 추가해둠.
	public void leaveChatRoom(Long roomId,Long memberId){
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
		ChatParticipant chatParticipant = chatParticipantRepository
			.findByChatRoomAndMemberAndActiveTrue(chatRoom, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
		if (chatParticipant.isHost()) {
			chatRoomRepository.delete(chatRoom);
			return;
		}

		chatParticipant.setActive(false);
		chatParticipant.setReady(false);

	public void joinChatRoom(Long roomId, Long memberId) {

		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		HostNotInOtherRoom(member);

		if(chatRoom.getStatus()!=RoomStatus.WAITING)
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);

		long current = chatParticipantRepository.countByChatRoomAndActiveTrue(chatRoom);
		if (current >= chatRoom.getMaxCapacity())
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_FULL);

		ChatParticipant chatParticipant = ChatParticipant.builder()
			.chatRoom(chatRoom)
			.member(member)
			.isActive(true)
			.build();

		chatParticipantRepository.save(chatParticipant);
	}

	private void HostNotInOtherRoom(Member host) {
		boolean exists = chatParticipantRepository.existsByMemberAndActiveTrue(host);
		if (exists)
			throw new CatxiException(ChatParticipantErrorCode.ALREADY_IN_ACTIVE_ROOM);

	}



}
