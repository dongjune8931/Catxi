package com.project.catxi.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatRoom;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
	List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom room);

	void deleteAllByChatRoom(ChatRoom chatRoom);
}

