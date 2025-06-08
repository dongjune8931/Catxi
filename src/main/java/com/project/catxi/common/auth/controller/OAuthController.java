package com.project.catxi.common.auth.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.service.CustomOAuth2UserService;
import com.project.catxi.common.auth.service.CustomUserDetailsService;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.member.service.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class OAuthController {

  private final CustomOAuth2UserService customOAuth2UserService;

  @GetMapping("/login/kakao")
  public ApiResponse<?> kakaoLogin(@RequestParam("code") String accessCode, HttpServletResponse response) {
    customOAuth2UserService.oAuthLogin(accessCode, response);

    return ApiResponse.success("로그인 성공");
  }

  //카카오 회원가입

  // 추가 회원가입 단계
  @PatchMapping("/signUp/catxi")
  public ResponseEntity<?> completeSignup(@RequestBody @Valid KakaoDTO.CatxiSignUp dto, @AuthenticationPrincipal CustomUserDetails userDetails) {

    customOAuth2UserService.catxiSignup(userDetails.getUsername(), dto);
    return ResponseEntity.ok("추가 회원정보 등록 완료");
  }


}
