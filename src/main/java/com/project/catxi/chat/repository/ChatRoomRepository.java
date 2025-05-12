package com.project.catxi.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatRoom;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {}
