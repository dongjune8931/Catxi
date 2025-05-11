package com.project.catxi.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.project.catxi.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

}

