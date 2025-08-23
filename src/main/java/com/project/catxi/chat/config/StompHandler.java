
package com.project.catxi.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;


import com.project.catxi.chat.service.ChatRoomService;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.jwt.JwtUtill;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompHandler implements ChannelInterceptor {

	private final JwtUtill jwtUtill;
	private final ChatRoomService chatRoomService;


	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

		if (StompCommand.CONNECT == accessor.getCommand()) {
			String token = extractToken(accessor);
			try {
				Claims claims = jwtUtill.parseJwt(token);
				jwtUtill.isExpired(claims);
				System.out.println("CONNECT - 토큰 유효성 검증 완료");
			} catch (Exception e) {
				throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
			}
		}

		if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
			log.info("SUBSCRIBE 진입");
			log.info("Authorization Header: {}", accessor.getFirstNativeHeader("Authorization"));
			log.info("Destination: {}", accessor.getDestination());

			String token = extractToken(accessor);
			Claims claims;
			String email;
			log.info("Email from token: {}", jwtUtill.getEmail(jwtUtill.parseJwt(token)));

			String destination = accessor.getDestination();
			if (destination == null || !destination.startsWith("/topic/")) {
				log.error("잘못된 destination: {}", destination);
				throw new AuthenticationServiceException("잘못된 destination입니다.");
			}

			String[] parts = destination.split("/");
			if (parts.length < 3) {
				log.error("destination split 오류: {}", (Object) parts);
				throw new AuthenticationServiceException("destination 파싱 오류");
			}

			String roomIdStr = parts[parts.length - 1];
			try {
				claims = jwtUtill.parseJwt(token);
				email = jwtUtill.getEmail(claims);
				Long roomId = Long.parseLong(roomIdStr);
				log.info("Room ID: {}", roomId);
				if (!chatRoomService.isRoomParticipant(email, roomId)) {
					log.warn("Room 참가자 아님: {} not in room {}", email, roomId);
					throw new AuthenticationServiceException("해당 room에 권한이 없습니다");
				}
			} catch (NumberFormatException e) {
				log.error("roomId가 숫자 아님: {}", parts[2]);
				throw new AuthenticationServiceException("roomId가 숫자가 아님");
			}
		}

		return message;
	}

	private String extractToken(StompHeaderAccessor accessor) {
		String bearerToken = accessor.getFirstNativeHeader("Authorization");
		if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
			throw new AuthenticationServiceException("Authorization 헤더가 잘못되었습니다");
		}
		return bearerToken.substring(7).trim();
	}


}
