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
import com.project.catxi.member.DTO.CustomUserDetails;
import com.project.catxi.member.domain.Member;

@RestController
@RequestMapping("/chat")
public class ChatController {

	private final ChatMessageService chatMessageService;
	private final ChatRoomService chatRoomService;

	public ChatController(ChatMessageService chatMessageService, ChatRoomService chatRoomService) {
		this.chatMessageService = chatMessageService;
		this.chatRoomService = chatRoomService;
	}

	@PostMapping("/room/create")
	public ResponseEntity<ApiResponse<RoomCreateRes>> createRoom(@RequestBody RoomCreateReq roomCreateReq,
		@AuthenticationPrincipal CustomUserDetails userDetails) {
		String membername = userDetails.getUsername();
		RoomCreateRes res = chatRoomService.createRoom(roomCreateReq, membername);
		return ResponseEntity.ok(ApiResponse.success(res));
	}


	@GetMapping("/{roomId}/messages")
	public ResponseEntity<ApiResponse<List<ChatMessageRes>>> getHistory(
		@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) {

		String membername = userDetails.getUsername();
		List<ChatMessageRes> history = chatMessageService.getChatHistory(roomId, membername);

		return ResponseEntity.ok(ApiResponse.success(history));
	}

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

	@DeleteMapping("/{roomId}/leave")
	public ResponseEntity<ApiResponse<Void>> leaveRoom(
		@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) {

		String membername = userDetails.getUsername();
		chatRoomService.leaveChatRoom(roomId, membername);
		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

	@PostMapping("/rooms/{roomId}/join")
	public ResponseEntity<ApiResponse<Void>> joinChatRoom(
		@PathVariable Long roomId,
		@AuthenticationPrincipal CustomUserDetails userDetails) {

		String membername = userDetails.getUsername();
		chatRoomService.joinChatRoom(roomId, membername);
		return ResponseEntity.ok(ApiResponse.successWithNoData());
	}

}
