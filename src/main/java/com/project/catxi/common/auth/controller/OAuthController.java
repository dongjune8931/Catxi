package com.project.catxi.common.auth.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.auth.kakao.BlacklistDTO;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.TokenDTO;
import com.project.catxi.common.auth.service.BlacklistService;
import com.project.catxi.common.auth.service.OAuthLoginService;
import com.project.catxi.common.auth.service.TokenService;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.dto.CustomUserDetails;
import com.project.catxi.member.repository.MemberRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
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

  private final OAuthLoginService oAuthLoginService;
  private final TokenService tokenService;
  private final BlacklistService blacklistService;
  private final MemberRepository memberRepository;

  @GetMapping("/kakao/callback")
  public ResponseEntity<Void> kakaoCallback(@RequestParam("code") String code) {
    HttpHeaders headers = new HttpHeaders();
    headers.setLocation(URI.create("https://catxi-university-taxi-b0936.web.app/home"));
    return new ResponseEntity<>(headers, HttpStatus.FOUND);
  }

  @GetMapping("/login/kakao")
  public ApiResponse<?> kakaoLogin(
      @RequestParam("code") String accessCode, HttpServletResponse response) {
    Member user = oAuthLoginService.kakaoLoginProcess(accessCode, response);

    if (user.getStatus() == MemberStatus.PENDING) {
      return ApiResponse.success("isNewUser");  }
    else {
      return ApiResponse.success("로그인 성공");  }
  }

  // 추가 회원가입 단계
  @PatchMapping("/signUp/catxi")
  public ApiResponse<?> completeSignup(@RequestBody @Valid KakaoDTO.CatxiSignUp dto, 
                                       @AuthenticationPrincipal CustomUserDetails userDetails) {
    tokenService.catxiSignup(userDetails.getUsername(), dto);
    return ApiResponse.success("추가 회원정보 등록 완료");
  }

  @Operation(summary = "닉네임 중복 조회")
  @GetMapping("/signUp/catxi/checkNN")
  public ApiResponse<Boolean> checkNN(@RequestParam("nickname") String nickname) {
    boolean isDuplicate = tokenService.isNNDuplicate(nickname);
    return ApiResponse.success(isDuplicate);
  }

  //Reissue
  @Transactional
  @PostMapping("/reissue")
  public ApiResponse<TokenDTO.Response> reissue(@CookieValue(name = "refresh", required = false)
                                                String refreshToken, HttpServletResponse response) {
    log.info("🍪 [Reissue 요청] 전달된 refreshToken 쿠키 값: {}", refreshToken);
    TokenDTO.Response tokenResponse = tokenService.reissueAccessToken(refreshToken, response);
    return ApiResponse.success(tokenResponse);
  }

  //로그아웃
  @PostMapping("/logout")
  public ApiResponse<?> logout(HttpServletRequest request,
                               @CookieValue(name = "refresh", required = false)
                               String refreshToken, HttpServletResponse response
  ) {
    tokenService.logout(request, refreshToken, response);
    return ApiResponse.success("로그아웃 완료");
  }

  //블랙리스트 등록
  @Operation(summary = "사용자 ID로 블랙리스트 등록", description = "사용자를 블랙리스트에 등록합니다. durationDays가 null이면 영구 등록됩니다.")
  @PostMapping("/uID")
  public ApiResponse<?> addUserToBlacklistById(@RequestBody @Valid BlacklistDTO.addUserBlacklist request) {

    blacklistService.addUserToBlacklistPermanent(request.userId());
    return ApiResponse.success("사용자가 블랙리스트에 등록되었습니다.");
  }

  //블랙리스트 해제
  @Operation(summary = "사용자 ID로 블랙리스트 해제", description = "사용자를 블랙리스트에서 해제합니다.")
  @DeleteMapping("/uID")
  public ApiResponse<?> removeUserFromBlacklistById(@RequestBody @Valid BlacklistDTO.removeUserBlacklist request) {
    blacklistService.removeUserFromBlacklist(request.userId());
    return ApiResponse.success("사용자가 블랙리스트에서 해제되었습니다.");
  }

  //블랙리스트 여부 조회
  @Operation(summary = "사용자 ID로 블랙리스트 상태 확인", description = "사용자가 블랙리스트에 등록되어 있는지 확인합니다.")
  @GetMapping("/blacklist/uID/{userId}")
  public ApiResponse<BlacklistDTO.checkBlacklist> checkBlacklistStatusById(@PathVariable Long userId) {

    boolean isBL = blacklistService.isUserBlacklisted(userId);

    Member member = memberRepository.findById(userId)
        .orElse(null);
    String email = member != null ? member.getEmail() : "Unknown";

    BlacklistDTO.checkBlacklist response
        = new BlacklistDTO.checkBlacklist(isBL, userId, email);

    return ApiResponse.success(response);
  }

  //TODO: 회원 탈퇴

}