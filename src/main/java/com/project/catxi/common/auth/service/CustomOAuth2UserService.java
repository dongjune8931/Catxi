package com.project.catxi.common.auth.service;

import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.KakaoUtill;
import com.project.catxi.common.config.JwtConfig;
import com.project.catxi.common.config.WebConfig;
import com.project.catxi.common.jwt.JwtUtill;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.member.service.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService {

  private final KakaoUtill kakaoUtill;
  private final JwtUtill jwtUtill;
  private final MemberRepository memberRepository;
  private final HttpServletResponse httpServletResponse;

  private final JwtConfig jwtConfig;

  public void oAuthLogin(String accessCode, HttpServletResponse response) {
    // 카카오 토큰 요청
    KakaoDTO.kakaoToken kakaoToken = kakaoUtill.requestToken(accessCode);
    // 사용자 정보 요청
    KakaoDTO.KakaoProfile kakaoProfile = kakaoUtill.requestProfile(kakaoToken);
    // 이메일로 기존 사용자 조회
    String requestEmail = kakaoProfile.kakao_account().email();
    Member user = memberRepository.findByEmail(requestEmail).orElseGet(()->createNewUser(kakaoProfile));

    // JWT 발급 후 응답 헤더에 추가
    loginProcess(httpServletResponse, user);
  }

  private Member createNewUser(KakaoDTO.KakaoProfile kakaoProfile) {

    String email = kakaoProfile.kakao_account().email();
    //실제 닉네임아니고 멤버 이름
    //동의항목에서 실명 제공 안해줌 Fuck you kakao
    String name = kakaoProfile.kakao_account().profile().nickname();

    log.info(">> name: " + kakaoProfile.kakao_account().profile().nickname());
    log.info(">> email: " + kakaoProfile.kakao_account().email());

    //nickname, studentNo는 서비스 내부 로직으로 삽입
    Member newUser = Member.builder()
        .email(email)
        .membername(name)
        //OAuth 쓰기 때문에 password 크게 의미 없음
        .password("NO_PASSWORD")
        .matchCount(0)
        .role("ROLE_USER")
        .build();

    return memberRepository.save(newUser);
  }

  private void loginProcess(HttpServletResponse httpServletResponse,Member member) {

    String name = member.getMembername();

    String access = jwtUtill.createJwt(
        "access",name,"ROLE_USER",jwtConfig.getAccessTokenValidityInSeconds());

    httpServletResponse.setHeader("access", access);
  }

  }
