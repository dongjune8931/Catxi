package com.project.catxi.chat.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.catxi.chat.dto.ChatMessageRes;
import com.project.catxi.chat.dto.ChatRoomRes;
import com.project.catxi.chat.dto.RoomCreateReq;
import com.project.catxi.chat.dto.RoomCreateRes;
import com.project.catxi.chat.service.ChatMessageService;
import com.project.catxi.chat.service.ChatRoomService;
import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.api.CommonPageResponse;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.domain.Member;

import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/chat")
public class ChatController {

	private final ChatMessageService chatMessageService;
	private final ChatRoomService chatRoomService;

	public ChatController(ChatMessageService chatMessageService, ChatRoomService chatRoomService) {
		this.chatMessageService = chatMessageService;
		this.chatRoomService = chatRoomService;
	}

	@Operation(summary = "채팅방 생성", description = "로그인한 사용자가 새로운 채팅방을 생성합니다.")
	@PostMapping("/room/create")
	public ResponseEntity<ApiResponse<RoomCreateRes>> createRoom(@RequestBody RoomCreateReq roomCreateReq,
		@AuthenticationPrincipal CustomUserDetails userDetails) {
		String email = userDetails.getUsername();
		RoomCreateRes res = chatRoomService.createRoom(roomCreateReq, email);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@Operation(summary = "채팅방 메시지 조회", description = "채팅방에 참여 중인 사용자가 해당 방의 메시지 이력을 조회합니다.")
	@GetMapping("/{roomId}/messages")
	public ResponseEntity<ApiResponse<List<ChatMessageRes>>> getHistory(
		@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) {

		String email = userDetails.getUsername();
		List<ChatMessageRes> history = chatMessageService.getChatHistory(roomId, email);

		return ResponseEntity.ok(ApiResponse.success(history));
	}

	@Operation(summary = "채팅방 목록 조회", description = "역 방향, 정류장, 정렬 기준, 페이지 정보를 기반으로 채팅방 목록을 조회합니다.")
	@GetMapping("/rooms")
	public ResponseEntity<ApiResponse<CommonPageResponse<ChatRoomRes>>> getRoomList(
		@RequestParam("direction") String direction,
		@RequestParam("station")String station,
		@RequestParam("sort") String sort,
		@RequestParam(value = "page", defaultValue = "0") int page
	) {
		Page<ChatRoomRes> roomList = chatRoomService.getChatRoomList(direction, station, sort, page);
		return ResponseEntity.ok(ApiResponse.success(CommonPageResponse.of(roomList)));
	}
	@Operation(summary = "채팅방 나가기", description = "로그인한 사용자가 해당 채팅방에서 나갑니다.")
	@DeleteMapping("/{roomId}/leave")
	public ResponseEntity<ApiResponse<Void>> leaveRoom(
		@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) {

		String email = userDetails.getUsername();
		chatRoomService.leaveChatRoom(roomId, email);
		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

	@Operation(summary = "채팅방 참여", description = "로그인한 사용자가 지정한 채팅방에 참여합니다.")
	@PostMapping("/rooms/{roomId}/join")
	public ResponseEntity<ApiResponse<Void>> joinChatRoom(
		@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) {

		String email = userDetails.getUsername();
		chatRoomService.joinChatRoom(roomId, email);
		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

}
