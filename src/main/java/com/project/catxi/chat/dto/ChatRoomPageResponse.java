package com.project.catxi.chat.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.project.catxi.common.api.CommonPageResponse;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatRoomPageResponse {
	private Long myRoomId;
	private CommonPageResponse<ChatRoomRes> rooms;

	public static ChatRoomPageResponse of(Page<ChatRoomRes> page, Long myRoomId) {
		return ChatRoomPageResponse.builder()
			.myRoomId(myRoomId)
			.rooms(CommonPageResponse.of(page))
			.build();
	}
}
