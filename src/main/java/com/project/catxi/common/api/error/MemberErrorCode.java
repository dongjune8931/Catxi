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

	DUPLICATE_AUTHORIZE_CODE(HttpStatus.BAD_REQUEST,"ACCESS402","인가 코드 중복 사용"),
	ACCESS_EXPIRED(HttpStatus.BAD_REQUEST,"ACCESS401","액세스 토큰이 만료되었습니다"),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED,"ACCESS400","유효하지 않은 토큰입니다"),
	ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN,"ACCESS403","탈퇴한 회원입니다."),

	REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "ACCESS404", "리프레시 토큰이 만료되었습니다."),
	REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "ACCESS405", "리프레시 토큰이 존재하지 않습니다."),
	REFRESH_TOKEN_MISMATCH(HttpStatus.UNAUTHORIZED, "ACCESS406", "리프레시 토큰이 일치하지 않습니다."),
	TOKEN_SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED, "ACCESS407", "토큰 서명이 올바르지 않습니다."),
	TOKEN_CLAIM_INVALID(HttpStatus.UNAUTHORIZED, "ACCESS410", "토큰 클레임 정보가 유효하지 않습니다."),

  MATCH_NOT_FOUND(HttpStatus.NOT_FOUND,"MATCH404","이용내역을 찾을 수 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String message;


}
