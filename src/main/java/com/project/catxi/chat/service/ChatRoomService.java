package com.project.catxi.chat.service;

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
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.RoomStatus;
import com.project.catxi.member.domain.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {


	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;

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
			.ready(true)
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


}
