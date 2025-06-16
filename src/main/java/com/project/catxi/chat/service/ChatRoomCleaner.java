package com.project.catxi.chat.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatRoomRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRoomCleaner {

	private final ChatRoomRepository chatRoomRepository;

	@Scheduled(cron = "0 0 * * * *") // 매 정시마다 실행
	public void deleteExpiredChatRooms() {
		LocalDateTime now = LocalDateTime.now();
		List<ChatRoom> expiredRooms = chatRoomRepository.findByDepartAtBefore(now);

		if (!expiredRooms.isEmpty()) {
			log.info("만료된 채팅방 {}개 삭제 시작", expiredRooms.size());
			chatRoomRepository.deleteAll(expiredRooms);
			log.info("만료된 채팅방 삭제 완료");
		} else {
			log.info("삭제할 만료 채팅방 없음");
		}
	}
}