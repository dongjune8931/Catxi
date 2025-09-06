package com.project.catxi.chat.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.member.domain.Member;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long>, ChatRoomRepositoryCustom {
	List<ChatRoom> findByDepartAtBefore(LocalDateTime time);
	
	List<ChatRoom> findAllByHost(Member host);
	
	@Modifying
	@Transactional
	void deleteAllByHost(Member host);
}

