package com.project.catxi.common.auth.service;

import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.KakaoUtill;
import com.project.catxi.common.config.WebConfig;
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
  private final WebConfig webConfig;
  private final MemberService memberService;
  private final MemberRepository memberRepository;

  public void oAuthLogin(String accessCode, HttpServletResponse httpServletResponse){
    KakaoDTO.kakaoToken kakaoToken = kakaoUtill.requestToken(accessCode);

    }


  }
