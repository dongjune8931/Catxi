package com.project.catxi.common.jwt;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.config.security.JwtConfig;
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
import java.util.Date;
import lombok.Data;
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
  private final JwtConfig jwtConfig;
  private final MemberRepository memberRepository;

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer";


  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    String uri = request.getRequestURI();

    // /connect, /auth/login/kakao로 시작하는 주소 JWT 검증 예외처리
    //TODO: uri 필터 통과 고민
    if (uri.startsWith("/connect") || uri.equals("/auth/login/kakao")) {
      log.info("JWT 검증 제외 경로로 통과: {}", uri);
      filterChain.doFilter(request, response);
      return;
    }

    //토큰 검증(헤더 확인)
    //Request에서 Authorization 헤더를 찾음
    String authorization = request.getHeader(AUTH_HEADER);
    if(authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      log.info("Token null");
      filterChain.doFilter(request, response);
      return;
    }

    //토큰 추출 Prefix 제거
    String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();

    //Claims 한 번에 전부 파싱
    Claims claims;
    try {
      claims = jwtUtil.parseJwt(accessToken);
    } catch (ExpiredJwtException e) {
      throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
    } catch (Exception e) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().print("유효하지 않은 토큰입니다");
      return;
    }

    // 토큰 만료 여부 확인
    if (jwtUtil.isExpired(claims).before(new Date())) {
      throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
    }

    // 토큰이 accessToken인지 확인
    String category = jwtUtil.getType(claims);
    if (!"access".equals(category)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().print("AccessToken이 아닙니다");
      return;
    }

    // jwtUtill 객체에서 username 받아와 DB에서 회원 확인 및 상태 점검
    String email = jwtUtil.getEmail(claims);
    Member member = memberRepository.findByEmail(email).orElse(null);
    if (member == null) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().print("Member not found");
      return;
    }

    // INACTIVE 회원 차단
    if (member.getStatus() == MemberStatus.INACTIVE) {
      log.info("✅ JWT 필터에서 INACTIVE 회원 차단: {}", email);
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
