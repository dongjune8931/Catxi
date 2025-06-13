package com.project.catxi.common.config;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.common.jwt.JwtUtill;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.repository.MemberRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class SseAuthenticationFilter extends OncePerRequestFilter {

	private final JwtUtill jwtUtill;
	private final MemberRepository memberRepository;

	@Override
	protected void doFilterInternal(HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		String acceptHeader = request.getHeader(HttpHeaders.ACCEPT);

		// SSE 요청이 아니면 패스
		if (acceptHeader == null || !acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
			filterChain.doFilter(request, response);
			return;
		}

		log.info("🔒 SSE 요청 감지: 인증 시도");

		String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			log.warn("⛔ Authorization 헤더 없음 또는 잘못된 형식");
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().write("Missing or invalid Authorization header");
			return;
		}

		String accessToken = authHeader.substring(7).trim();

		try {
			jwtUtill.isExpired(accessToken);
		} catch (ExpiredJwtException e) {
			throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
		}

		String category = jwtUtill.getCategory(accessToken);
		if (!category.equals("access")) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().write("Invalid token category");
			return;
		}

		String email = jwtUtill.getEmail(accessToken);
		Member member = memberRepository.findByEmail(email).orElse(null);

		if (member == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.getWriter().write("Member not found");
			return;
		}

		if (member.getStatus() == MemberStatus.INACTIVE) {
			throw new MemberHandler(MemberErrorCode.ACCESS_FORBIDDEN);
		}

		CustomUserDetails userDetails = new CustomUserDetails(member);
		Authentication authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

		SecurityContextHolder.getContext().setAuthentication(authToken);

		log.info("✅ SSE 인증 성공: {}", email);

		filterChain.doFilter(request, response);
	}
}
