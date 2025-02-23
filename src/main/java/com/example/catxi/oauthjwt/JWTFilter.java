package com.example.catxi.oauthjwt;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.catxi.member.dto.MemberDTO;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JWTFilter extends OncePerRequestFilter {

	private final JWTUtil jwtUtil;

	public JWTFilter(JWTUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {

		String authorization = null;
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("Authorization".equals(cookie.getName())) {
					authorization = cookie.getValue();
				}
			}
		}
		if (authorization == null) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = authorization;
		if (jwtUtil.isExpired(token)) {
			filterChain.doFilter(request, response);
			return;
		}

		String username = jwtUtil.getUsername(token);
		String role = jwtUtil.getRole(token);

		MemberDTO memberDTO = new MemberDTO();
		memberDTO.setUsername(username);
		memberDTO.setRole(role);

		CustomOAuth2User customUser = new CustomOAuth2User(memberDTO);
		Authentication authToken = new UsernamePasswordAuthenticationToken(customUser, null, customUser.getAuthorities());
		SecurityContextHolder.getContext().setAuthentication(authToken);

		filterChain.doFilter(request, response);
	}
}