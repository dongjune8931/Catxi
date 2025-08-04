package com.project.catxi.common.api.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MapError implements ErrorCode{

	COORDINATE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "MAP400", "좌표 파싱에 실패했습니다."),
	COORDINATE_NOT_FOUND(HttpStatus.NOT_FOUND, "MAP404", "좌표를 찾을 수 없습니다."),
	COORDINATE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MAP500", "좌표 저장에 실패했습니다."),
	DEPARTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "MAP404", "출발지 좌표를 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;
}
