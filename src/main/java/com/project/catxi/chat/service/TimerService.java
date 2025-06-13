package com.project.catxi.chat.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.catxi.chat.domain.ChatRoom;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.common.api.error.ChatRoomErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.domain.RoomStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TimerService {

	private static final Logger log = LoggerFactory.getLogger(TimerService.class);
	private final RedisTemplate<String, String> redisTemplate;
	private final TaskScheduler taskScheduler;
	private final ChatRoomRepository chatRoomRepository;
	private final ChatParticipantRepository chatParticipantRepository;

	public void scheduleReadyTimeout(String roomId) {
		ChatRoom room = chatRoomRepository.findById(Long.valueOf(roomId))
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		long participantCount = chatParticipantRepository.countByChatRoom(room);

		// Redis에 당시 참여자 수 저장
		redisTemplate.opsForValue().set("ready:" + roomId, String.valueOf(participantCount), Duration.ofSeconds(15));

		// TaskScheduler로 10초 뒤 작업 예약
		taskScheduler.schedule(() -> {
				try {
					checkAndUpdateRoomStatus(roomId);
				} catch (Exception e) {
					log.error("Error checking and updating room status for roomId: {}", roomId, e);
				}
			},
			Instant.now().plusSeconds(10)
		);
	}

	@Transactional
	public void checkAndUpdateRoomStatus(String roomId) {
		ChatRoom room = chatRoomRepository.findById(Long.valueOf(roomId))
			.orElseThrow(() -> new CatxiException(ChatRoomErrorCode.CHATROOM_NOT_FOUND));

		String savedCountStr = redisTemplate.opsForValue().get("ready:" + roomId);

		if (room.getStatus() != RoomStatus.READY_LOCKED) {
			return;
		}

		if (savedCountStr == null) {
			// Redis 키가 사라졌거나 문제가 생긴 경우
			chatParticipantRepository.updateIsReadyFalseExceptHost(room.getRoomId());
			room.setStatus(RoomStatus.WAITING);
		} else {
			long savedCount = Long.parseLong(savedCountStr);
			long currentCount = chatParticipantRepository.countByChatRoomAndIsReady(room, true);

			if (savedCount == currentCount) {
				room.matchedStatus(RoomStatus.MATCHED);
			} else {
				chatParticipantRepository.deleteAllByChatRoomAndIsReadyFalse(room);
				chatParticipantRepository.updateIsReadyFalseExceptHost(room.getRoomId());
				room.setStatus(RoomStatus.WAITING);
			}
		}

		chatRoomRepository.save(room);
		redisTemplate.delete("ready:" + roomId);
	}


}