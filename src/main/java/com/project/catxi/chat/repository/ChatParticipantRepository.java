package com.project.catxi.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatMessage;
import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant,Long> {

	boolean existsByMemberAndActiveTrue(Member member);

	List<ChatMessage> findByChatRoomOrderByCreatedTimeAsc(ChatRoom room);

	boolean existsByChatRoomAndMember(ChatRoom room, Member member);

}
