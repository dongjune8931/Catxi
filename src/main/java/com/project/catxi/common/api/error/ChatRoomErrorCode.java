package com.project.catxi.common.api.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatRoomErrorCode implements ErrorCode{

	CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "PORTFOLIO404", "채팅방를 찾을 수 없습니다."),
	NOT_OWNED_CHATROOM(HttpStatus.FORBIDDEN, "PORTFOLIO403", "본인 소유의 채팅방이 아닙니다."),
	INVALID_CHATROOM_PARAMETER(HttpStatus.BAD_REQUEST, "PORTFOLIO400", "채팅방 요청 데이터가 올바르지 않습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
