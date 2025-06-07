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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService {

  private final KakaoUtill kakaoUtill;
  private final JwtUtill jwtUtill;
  private final MemberRepository memberRepository;
  private final HttpServletResponse httpServletResponse;

  private final JwtConfig jwtConfig;

  public void oAuthLogin(String accessCode, HttpServletResponse response) {
    KakaoDTO.kakaoToken kakaoToken = kakaoUtill.requestToken(accessCode);
    KakaoDTO.KakaoProfile kakaoProfile = kakaoUtill.requestProfile(kakaoToken);
    String requestEmail = kakaoProfile.kakao_account().email();

    Member byEmail = memberRepository.findByEmail(requestEmail)
        .orElseGet(() -> createNewUser(kakaoProfile));

    loginProcess(httpServletResponse, byEmail);
  }

  private Member createNewUser(KakaoDTO.KakaoProfile kakaoProfile) {

    String email = kakaoProfile.kakao_account().email();
    String nickname = kakaoProfile.kakao_account().profile().nickname();

    Member newUser = Member.builder()
        .email(email)
        .nickname(nickname)
        //OAuth 쓰기 때문에 password 크게 의미 없음
        .password(null)
        .role("ROLE_USER")
        .build();

    return memberRepository.save(newUser);
  }

  private void loginProcess(HttpServletResponse httpServletResponse,Member byEmail) {
    String username = byEmail.getMembername();

    String access = jwtUtill.createJwt("access",username,"ROLE_USER",jwtConfig.getAccessTokenValidityInSeconds());

    httpServletResponse.setHeader("access", access);

  }

  }
