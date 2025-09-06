package com.project.catxi.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom room);

	@Modifying
	@Transactional
	void deleteAllByChatRoom(ChatRoom chatRoom);
	
	@Modifying
	@Transactional
	void deleteAllByMember(Member member);
}

