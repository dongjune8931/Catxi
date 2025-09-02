package com.project.catxi.common.jwt;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.auth.infra.TokenBlacklistRepository;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final MemberRepository memberRepository;
  private final TokenBlacklistRepository tokenBlacklistRepository;

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";


  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    String uri = request.getRequestURI();

    // /connect, /auth/login/kakao로 시작하는 주소 JWT 검증 예외처리
    if (uri.startsWith("/connect") || uri.equals("/auth/login/kakao")) {
      log.info("JWT 검증 제외 경로로 통과: {}", uri);
      filterChain.doFilter(request, response);
      return;
    }

    //토큰 검증(헤더 확인)
    //Request에서 Authorization 헤더를 찾음
    String authorization = request.getHeader(AUTH_HEADER);
    if(authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      log.info("Authorization 헤더가 없거나 Bearer 토큰이 아닙니다");
      filterChain.doFilter(request, response);
      return;
    }

    //토큰 추출 Prefix 제거
    String accessToken = authorization.substring(BEARER_PREFIX.length());

    //Claims 한 번에 전부 파싱
    Claims claims;
    try {
      claims = jwtUtil.parseJwt(accessToken);
    } catch (ExpiredJwtException e) {
      throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
    } catch (Exception e) {
      throw new MemberHandler(MemberErrorCode.INVALID_TOKEN);
    }


    // 토큰이 accessToken인지 확인
    String category = jwtUtil.getType(claims);
    if (!"access".equals(category)) {
      throw new MemberHandler(MemberErrorCode.INVALID_TOKEN);
    }

    // accessToken 블랙리스트 여부 조회
    if (tokenBlacklistRepository.isTokenBlacklisted(accessToken)) {
      log.info("🚨 블랙리스트에 등록된 토큰: {}", accessToken);
      throw new MemberHandler(MemberErrorCode.ACCESS_FORBIDDEN);
    }

    // jwtUtil 객체에서 username 받아와 DB에서 회원 확인 및 상태 점검
    String email = jwtUtil.getEmail(claims);
    Member member = memberRepository.findByEmail(email).orElse(null);
    if (member == null) {
      throw new MemberHandler(MemberErrorCode.MEMBER_NOT_FOUND);
    }

    // INACTIVE 회원 차단
    if (member.getStatus() == MemberStatus.INACTIVE) {
      log.info("✅ JWT 필터에서 INACTIVE 회원 차단: {}", email);
      throw new MemberHandler(MemberErrorCode.ACCESS_FORBIDDEN);
    }

    // User 블랙리스트 여부 조회
    if (tokenBlacklistRepository.isUserBlacklisted(member.getId().toString())) {
      log.info("🚨 블랙리스트에 등록된 사용자: {}", email);
      throw new MemberHandler(MemberErrorCode.ACCESS_FORBIDDEN);
    }

    // UserDetails에 회원 정보 객체 담기
    CustomUserDetails customUserDetails = new CustomUserDetails(member);

    // 스프링 시큐리티 인증 토큰 생성
    Authentication authToken = new UsernamePasswordAuthenticationToken(customUserDetails, null, customUserDetails.getAuthorities());
    // 세션에 사용자 등록 (AuthToken 시큐리티 컨텍스트 홀더에 넣어줌)
    SecurityContextHolder.getContext().setAuthentication(authToken);

    // 그 다음 필터에 request,response 전달
    filterChain.doFilter(request, response);
  }

}
