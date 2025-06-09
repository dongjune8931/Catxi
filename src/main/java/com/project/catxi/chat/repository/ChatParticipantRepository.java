package com.project.catxi.chat.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant,Long> {

	boolean existsByMember(Member member);

	List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom room);

	boolean existsByChatRoomAndMember(ChatRoom room, Member member);


	Optional<ChatParticipant> findByChatRoomAndMember(ChatRoom room, Member member);


	long countByChatRoom(ChatRoom room);

	long countByChatRoomAndIsReady(ChatRoom chatRoom, boolean ready);


	List<ChatParticipant> findByChatRoom(ChatRoom chatRoom);

}
