package com.project.catxi.common.jwt;

import com.project.catxi.member.DTO.CustomUserDetails;
import com.project.catxi.member.domain.Member;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtFilter extends OncePerRequestFilter {

  private final JwtUtill jwtUtill;

  public JwtFilter(JwtUtill jwtUtill) {
    this.jwtUtill = jwtUtill;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

    //토큰 검증
    // Request에서 Authorization 헤더를 찾음
    String authorization = request.getHeader("Authorization");

    // Authorization 헤더 검증
    if(authorization == null || !authorization.startsWith("Bearer ")) {
      System.out.println("token null");
      //request 필터 종료하고 다음 필터로 넘겨줌
      filterChain.doFilter(request, response);

      return;
    }

    System.out.println("authorization now");

    //토큰 분리 -> 토큰에 대한 소멸시간 검증
    String token = authorization.split(" ")[1];

    if(jwtUtill.isExpired(token)) {
      System.out.println("token expired");
      filterChain.doFilter(request, response);

      return;
    }

    // jwtUtill 객체에서 username 받아옴
    String membername = jwtUtill.getMembername(token);

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
