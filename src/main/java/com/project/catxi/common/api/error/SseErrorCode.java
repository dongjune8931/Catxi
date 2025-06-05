package com.project.catxi.common.api.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SseErrorCode implements ErrorCode{

	SSE_ROOM_BLOCKED(HttpStatus.FORBIDDEN, "SSE403", "해당 채팅방은 현재 입장이 불가능합니다."),
	SSE_CONNECTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SSE500", "SSE 연결에 문제가 발생했습니다."),
	SSE_SEND_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SSE500", "SSE 메시지 전송 중 오류가 발생했습니다."),
	SERVER_SSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SSE500", "서버에서 SSE 처리 중 오류가 발생했습니다."),
	SSE_NOT_FOUND(HttpStatus.NOT_FOUND, "SSE404", "SSE 리소스를 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
