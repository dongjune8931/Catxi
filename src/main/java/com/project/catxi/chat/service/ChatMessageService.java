package com.project.catxi.chat.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.dto.ChatMessageSendReq;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
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


	public void saveMessage(ChatMessageSendReq req){
		ChatRoom room = chatRoomRepository.findById(req.roomId())
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		Member sender = memberRepository.findById(req.senderId())
			.orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

		ChatMessage chatMsg = ChatMessage.builder()
			.chatRoom(room)
			.member(sender)
			.content(req.message())
			.msgType(MessageType.CHAT)
			.build();

		chatMessageRepository.save(chatMsg);
	}





}
