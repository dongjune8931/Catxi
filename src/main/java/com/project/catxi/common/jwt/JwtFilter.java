package com.project.catxi.common.jwt;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.config.JwtConfig;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.domain.Member;
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

  private final JwtUtill jwtUtill;
  private final JwtConfig jwtConfig;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    //토큰 검증
    // Request에서 Authorization 헤더를 찾음
    String authorization = request.getHeader("Authorization");

    // Authorization 헤더가 없거나 Bearer 스킴 없다면
    if(authorization == null || !authorization.startsWith("Bearer ")) {
      log.info("Token null");
      //request 필터 종료하고 다음 필터로 넘겨줌
      filterChain.doFilter(request, response);
      return;
    }

    // 토큰 분리 -> 토큰에 대한 소멸시간 검증
    String accessToken = authorization.substring(7).trim();

    // 토큰 만료 여부 확인
    try {
      log.info("토큰 존재 검증 시작");
      jwtUtill.isExpired(accessToken);
    } catch (ExpiredJwtException e) {
      throw new MemberHandler(MemberErrorCode.ACCESS_EXPIRED);
    }

    // 토큰이 accessToken인지 확인
    String category = jwtUtill.getCategory(accessToken);
    if (!category.equals("access")) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().print("invalid access token");
      return;
    }

    // jwtUtill 객체에서 username 받아옴
    String membername = jwtUtill.getMembername(accessToken);

    // Member를 생성하여 값 초기화
    // 비밀번호 값은 token에 담겨있지 않았음 -> 임시적으로 비밀번호 생성하여 넣어둠
    Member member = new Member();
    member.setMembername(membername);
    member.setPassword("CatxiPassword");

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
