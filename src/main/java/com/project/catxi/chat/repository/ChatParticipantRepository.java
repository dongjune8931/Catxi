package com.project.catxi.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatParticipant;
import com.project.catxi.member.domain.Member;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant,Long> {

	boolean existsByMemberAndActiveTrue(Member member);
}
