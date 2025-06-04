
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


@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

	private final JwtUtill jwtUtill;
	private final ChatRoomService chatRoomService;


	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

		if (StompCommand.CONNECT == accessor.getCommand()) {
			String token = extractToken(accessor);
			try {
				jwtUtill.isExpired(token);
				System.out.println("CONNECT - 토큰 유효성 검증 완료");
			} catch (Exception e) {
				throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
			}
		}

		if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
			String token = extractToken(accessor);
			String membername = jwtUtill.getMembername(token);
			String roomId = accessor.getDestination().split("/")[2];
			if (!chatRoomService.isRoomParticipant(membername, Long.parseLong(roomId))) {
				throw new AuthenticationServiceException("해당 room에 권한이 없습니다");
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
