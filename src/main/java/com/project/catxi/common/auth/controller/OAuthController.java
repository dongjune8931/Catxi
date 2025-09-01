package com.project.catxi.common.auth.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.auth.infra.CodeCache;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.TokenDTO;
import com.project.catxi.common.auth.service.CustomOAuth2UserService;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.dto.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/auth")
public class OAuthController {

  private final CustomOAuth2UserService oAuth2UserService;
  private final CodeCache codeCache;

  @GetMapping("/kakao/callback")
  public ResponseEntity<Void> kakaoCallback(@RequestParam("code") String code) {
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create("http://localhost:5173/callback/kakao?code=" + code));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }

  @GetMapping("/login/kakao")
  public ApiResponse<?> kakaoLogin(@RequestParam("code") String accessCode, HttpServletResponse response) {
    // 중복 코드 차단
    if (codeCache.isDuplicate(accessCode)) {
      log.warn("🚨중복 code 요청 차단 code = {}", accessCode);
      return ApiResponse.error(MemberErrorCode.DUPLICATE_AUTHORIZE_CODE);
    }

    try {
      // 로그인 처리
      Member user = oAuth2UserService.oAuthLogin(accessCode, response);
      String email = user.getEmail();

      // ✅ loginProcess에서 토큰 발급 및 저장 처리

      // 회원 상태에 따라 결과 반환
      if (user.getStatus() == MemberStatus.PENDING) {
        return ApiResponse.success("isNewUser");
      } else {
        return ApiResponse.success("로그인 성공");
      }
    } catch (Exception e) {
      log.error("[카카오 로그인 실패] code = {}, error = {}", accessCode, e.getMessage());
      codeCache.remove(accessCode);
      return ApiResponse.error(MemberErrorCode.ACCESS_EXPIRED);
    }
  }

  // 추가 회원가입 단계
  @PatchMapping("/signUp/catxi")
  public ApiResponse<?> completeSignup(@RequestBody @Valid KakaoDTO.CatxiSignUp dto, 
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
    oAuth2UserService.catxiSignup(userDetails.getUsername(), dto);
    return ApiResponse.success("추가 회원정보 등록 완료");
  }

  @Operation(summary = "닉네임 중복 조회")
  @GetMapping("/signUp/catxi/checkNN")
  public ApiResponse<Boolean> checkNN(@RequestParam("nickname") String nickname) {
    boolean isDuplicate = oAuth2UserService.isNNDuplicate(nickname);
    return ApiResponse.success(isDuplicate);
  }

  //Reissue
  @Transactional
  @PostMapping("/reissue")
  public ApiResponse<TokenDTO.Response> reissue(@CookieValue(name = "refresh", required = false)
                                                String refreshToken, HttpServletResponse response) {
    log.info("🍪 [Reissue 요청] 전달된 refreshToken 쿠키 값: {}", refreshToken);
    TokenDTO.Response tokenResponse = oAuth2UserService.reissueAccessToken(refreshToken, response);
    return ApiResponse.success(tokenResponse);
  }

  //로그아웃
  @PostMapping("/logout")
  public ApiResponse<?> logout(@CookieValue(name = "refresh", required = false)
                               String refreshToken, HttpServletResponse response
  ) {
    log.info("✅ logout 성공");
    oAuth2UserService.logout(refreshToken, response);
    return ApiResponse.success("로그아웃 완료");
  }

  //TODO: 회원 탈퇴

}