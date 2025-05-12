package com.project.catxi.chat.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.catxi.chat.dto.ChatMessageRes;
import com.project.catxi.chat.dto.RoomCreateReq;
import com.project.catxi.chat.dto.RoomCreateRes;
import com.project.catxi.chat.service.ChatMessageService;
import com.project.catxi.chat.service.ChatRoomService;
import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.api.CommonPageResponse;
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
		Member member) {
		RoomCreateRes res = chatRoomService.creatRoom(roomCreateReq, member);
		return ResponseEntity.ok(ApiResponse.success(res));
	}

	@GetMapping("/{roomId}/messages")
	public ResponseEntity<ApiResponse<List<ChatMessageRes>>> getHistory(@PathVariable Long roomId, @RequestParam("memberId") Long memberId){
		List<ChatMessageRes> history =
			chatMessageService.getChatHistory(roomId, memberId);

		return  ResponseEntity.ok(ApiResponse.success(history));
	}
}
