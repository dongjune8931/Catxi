package com.project.catxi.common.api.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode{
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404", "멤버를 찾을 수 없습니다."),
	INVALID_MEMBER_PARAMETER(HttpStatus.BAD_REQUEST, "MEMBER400", "유효하지 않는 멤버입니다"),
	DUPLICATE_MEMBER_STUDENTNO(HttpStatus.BAD_REQUEST, "MEMBER401", "이미 가입된 학번입니다"),

	ACCESS_EXPIRED(HttpStatus.BAD_REQUEST,"ACCESS401","액세스 토큰이 만료되었습니다"),
	DUPLICATE_AUTHORIZE_CODE(HttpStatus.BAD_REQUEST,"ACCESS402","인가 코드 중복 사용");
	
	private final HttpStatus httpStatus;
	private final String code;
	private final String message;


}
