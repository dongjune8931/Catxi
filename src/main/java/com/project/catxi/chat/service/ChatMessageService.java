package com.project.catxi.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import com.project.catxi.fcm.service.FcmQueueService;
import com.project.catxi.fcm.service.FcmActiveStatusService;
import com.project.catxi.common.util.ServerInstanceUtil;

import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {

	private final ChatRoomRepository chatRoomRepository;
	private final MemberRepository memberRepository;
	private final ChatMessageRepository chatMessageRepository;
	private final ChatParticipantRepository chatParticipantRepository;
	private final ObjectMapper objectMapper;
	private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
	private final FcmQueueService fcmQueueService;
	private final FcmActiveStatusService fcmActiveStatusService;
	private final ServerInstanceUtil serverInstanceUtil;

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
		
		// FCM 알림 이벤트 발행 (상대방에게) - 저장된 메시지 ID 포함
		sendChatNotificationToOthers(room, sender, chatMsg.getId(), req.message());
	}
	
	private void sendChatNotificationToOthers(ChatRoom room, Member sender, Long messageId, String message) {
		try {
			// 현재 서버가 이 Room의 FCM 처리를 담당하는지 확인
			if (!serverInstanceUtil.shouldProcessFcmForRoom(room.getRoomId())) {
				log.debug("FCM 처리 스킵 - 다른 서버에서 처리: RoomId={}, ServerId={}", 
						room.getRoomId(), serverInstanceUtil.getServerInstanceId());
				return;
			}
			
			log.debug("FCM 처리 시작 - 현재 서버에서 처리: RoomId={}, ServerId={}", 
					room.getRoomId(), serverInstanceUtil.getServerInstanceId());
			
			// 방에 참여한 다른 사용자들 조회 (발송자 제외)
			List<ChatParticipant> participants = chatParticipantRepository.findByChatRoom(room);
			
            participants.stream()
                .filter(participant -> participant.getMember() != null)
                .filter(participant -> !participant.getMember().getId().equals(sender.getId()))
                .filter(participant -> !fcmActiveStatusService.isUserActiveInRoom(
                    participant.getMember().getId(), room.getRoomId()))
                .forEach(participant -> {
                    fcmQueueService.publishChatNotification(
                        participant.getMember().getId(),
                        room.getRoomId(),
                        messageId, // 메시지 ID 포함
                        sender.getNickname() != null ? sender.getNickname() : sender.getMembername(),
                        message
                    );
                });
				
		} catch (Exception e) {
			// FCM 알림 실패가 채팅 저장을 방해하지 않도록 예외 처리
			log.error("채팅 FCM 알림 이벤트 발행 실패 - Room ID: {}, Sender: {}, MessageId: {}", 
				room.getRoomId(), sender.getId(), messageId, e);
		}
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
				m.getMember() != null ? m.getMember().getId()        : null,
				m.getMember() != null ? m.getMember().getNickname()  : "[SYSTEM]",
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
			redisTemplate.convertAndSend("chat", json); // ✅
		} catch (JsonProcessingException e) {
			throw new RuntimeException("시스템 메시지 직렬화 실패", e);
		}

	}
}
