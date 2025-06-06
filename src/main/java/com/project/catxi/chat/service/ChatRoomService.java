package com.project.catxi.chat.service;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.ChatRoomRes;
import com.project.catxi.chat.dto.RoomCreateReq;
import com.project.catxi.chat.dto.RoomCreateRes;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.Location;
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


	public RoomCreateRes createRoom(RoomCreateReq roomReq, String membername) {
		Member host = memberRepository.findByMembername(membername)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		HostNotInOtherRoom(host);

		if (roomReq.startPoint().equals(roomReq.endPoint())) {
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);
		}

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
			.active(true)
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


	public Page<ChatRoomRes> getChatRoomList(String direction, String station, String sort, Integer page) {
		Pageable pageable = PageRequest.of(page, 10, Sort.by(sort));

		Location location = switch (station) {
			case "SOSA_ST" -> Location.SOSA_ST;
			case "YEOKGOK_ST" -> Location.YEOKGOK_ST;
			default -> Location.GURO_ST;
		};

		return chatRoomRepository.findByLocationAndDirection(location, direction, pageable);
	}


	//로그인 전이라 member 임시로 추가해둠.
	public void leaveChatRoom(Long roomId, String membername) {
		Member member = memberRepository.findByMembername(membername).orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));
		ChatParticipant chatParticipant = chatParticipantRepository
			.findByChatRoomAndMember(chatRoom, member)
			.orElseThrow(() -> new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND));
		if (chatParticipant.isHost()) {
			chatRoomRepository.delete(chatRoom);
			return;
		}

		chatParticipantRepository.delete(chatParticipant);
	}

	public void joinChatRoom(Long roomId, String membername) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member member = memberRepository.findByMembername(membername)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		HostNotInOtherRoom(member);

		if(chatRoom.getStatus() != RoomStatus.WAITING)
			throw new CatxiException(ChatRoomErrorCode.INVALID_CHATROOM_PARAMETER);

		long current = chatParticipantRepository.countByChatRoomAndActiveTrue(chatRoom);
		if (current >= chatRoom.getMaxCapacity())
			throw new CatxiException(ChatRoomErrorCode.CHATROOM_FULL);

		ChatParticipant chatParticipant = ChatParticipant.builder()
			.chatRoom(chatRoom)
			.member(member)
			.active(true)
			.build();

		chatParticipantRepository.save(chatParticipant);
	}


	private void HostNotInOtherRoom(Member host) {
		boolean exists = chatParticipantRepository.existsByMemberAndActiveTrue(host);
		if (exists)
			throw new CatxiException(ChatParticipantErrorCode.ALREADY_IN_ACTIVE_ROOM);

	}

	public boolean isRoomParticipant(String membername, Long roomId) {
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new EntityNotFoundException("room not found"));
		Member member = memberRepository.findByMembername(membername)
			.orElseThrow(() -> new EntityNotFoundException("member not found"));
		List<ChatParticipant> chatParticipants = chatParticipantRepository.findByChatRoom(chatRoom);
		for (ChatParticipant c : chatParticipants) {
			if (c.getMember().equals(member)) {
				return true;
			}
		}
		return false;

	}


}
