package com.project.catxi.common.config.security;

import com.project.catxi.common.auth.infra.TokenBlacklistRepository;
import com.project.catxi.common.jwt.JwtFilter;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.member.repository.MemberRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;

@Component
public class JwtFilterConfig {

  private final JwtUtil jwtUtil;
  private final JwtConfig jwtConfig;
  private final MemberRepository memberRepository;
  private final TokenBlacklistRepository tokenBlacklistRepository;

  public JwtFilterConfig(JwtUtil jwtUtil, JwtConfig jwtConfig, MemberRepository memberRepository, TokenBlacklistRepository tokenBlacklistRepository) {
    this.jwtUtil = jwtUtil;
    this.jwtConfig = jwtConfig;
    this.memberRepository = memberRepository;
    this.tokenBlacklistRepository = tokenBlacklistRepository;
  }

  public void configureJwtFilters(HttpSecurity http) throws Exception {
    // JwtFilter - 토큰 검증 및 인증 객체 설정
    http.addFilterBefore(
        new JwtFilter(jwtUtil, memberRepository, tokenBlacklistRepository), UsernamePasswordAuthenticationFilter.class);
  }

}