package com.project.catxi.chat.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.project.catxi.chat.service.ChatRoomService;
import com.project.catxi.chat.service.ReadyService;
import com.project.catxi.chat.service.SseService;
import com.project.catxi.common.api.ApiResponse;

import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.member.DTO.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sse")
public class SseController {
	private final SseService sseService;
	private final ChatRoomService chatRoomService;
	private final ReadyService readyService;

	// 클라이언트로부터 SSE 연결 요청을 받는 엔드포인트
	@GetMapping(value="/subscribe/{roomId}", produces = "text/event-stream")
	public Object subscribe(@PathVariable String roomId,
			@AuthenticationPrincipal CustomUserDetails userDetails) {
	    try {
			boolean isHost = chatRoomService.isHost(Long.valueOf(roomId), userDetails.getUsername());
			boolean isParticipant = chatRoomService.isRoomParticipant(userDetails.getUsername(), Long.valueOf(roomId));

			return sseService.subscribe(roomId, userDetails.getUsername(), isHost, isParticipant);
		} catch (CatxiException e) {
			return sseService.createErrorEmitter(e.getErrorCode().getMessage());
		}
	}

	// 클라이언트로부터 메시지를 받아 해당 채팅방에 있는 모든 클라이언트에게 전송하는 엔드포인트
	@PostMapping("/request/{roomId}")
	public ResponseEntity<ApiResponse<Void>> sendMessage(@PathVariable String roomId,
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		readyService.requestReady(roomId, userDetails.getUsername());

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

	// 클라이언트로부터 메시지를 받아 해당 채팅방의 방장에게 전송하는 엔드포인트
	@PostMapping("/accept/{roomId}")
	public ResponseEntity<ApiResponse<Void>> sendMessageToHost(@PathVariable String roomId,
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		readyService.acceptReady(roomId, userDetails.getUsername());

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

	@PostMapping("/reject/{roomId}")
	public ResponseEntity<ApiResponse<Void>> rejectReady(@PathVariable String roomId,
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		readyService.rejectReady(roomId, userDetails.getUsername());
		chatRoomService.leaveChatRoom(Long.valueOf(roomId), userDetails.getUsername());

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

	// disconnect 이벤트를 처리하는 엔드포인트
	@DeleteMapping("/disconnect/{roomId}")
	public ResponseEntity<ApiResponse<Void>> disconnect(@PathVariable String roomId,
			@AuthenticationPrincipal CustomUserDetails userDetails) {
		boolean isHost = chatRoomService.isHost(Long.valueOf(roomId), userDetails.getUsername());
		sseService.disconnect(roomId, userDetails.getUsername(), isHost);

		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}
}
