package com.project.catxi.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.ChatMessageRes;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatParticipantErrorCode;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.MessageType;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {

	private final ChatRoomRepository chatRoomRepository;
	private final MemberRepository memberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final RedisPubSubService pubSubService;
	private final ObjectMapper objectMapper;

	public void saveMessage(Long roomId,ChatMessageSendReq req) {
		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member sender = memberRepository.findByEmail(req.email())
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatMessage chatMsg = ChatMessage.builder()
			.chatRoom(room)
			.member(sender)
			.content(req.message())
			.msgType(MessageType.CHAT)
			.build();

		chatMessageRepository.save(chatMsg);
	}

	public List<ChatMessageRes> getChatHistory(Long roomId, String email) {

		Member member = memberRepository.findByEmail(email)
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatRoom room = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		if (!chatParticipantRepository.existsByChatRoomAndMember(room, member)) {
			throw new CatxiException(ChatParticipantErrorCode.PARTICIPANT_NOT_FOUND);
		}

		return chatMessageRepository.findByChatRoomOrderByCreatedTimeAsc(room)
			.stream()
			.map(m -> new ChatMessageRes(
				m.getMember() !=null ? m.getMember().getEmail(): "[SYSTEM]",
				m.getId(),
				room.getRoomId(),
				m.getMember().getId(),
				m.getMember().getNickname(),
				m.getContent(),
				m.getCreatedTime()
			))
			.toList();

	}

	public void sendSystemMessage(Long roomId, String content){
		ChatRoom chatRoom = chatRoomRepository.findById(roomId)
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		ChatMessage systemMsg = ChatMessage.builder()
			.chatRoom(chatRoom)
			.member(null)
			.content(content)
			.msgType(MessageType.SYSTEM)
			.build();

		chatMessageRepository.save(systemMsg);

		ChatMessageSendReq dto = new ChatMessageSendReq(
			roomId,
			"[SYSTEM]",
			content,
			LocalDateTime.now()
		);

		try {
			String json = objectMapper.writeValueAsString(dto);
			pubSubService.publish("chat", json);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("시스템 메시지 직렬화 실패", e);
		}


	}
}
