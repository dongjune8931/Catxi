package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.api.handler.MemberHandler;
import com.project.catxi.common.auth.infra.CodeCache;
import com.project.catxi.common.auth.infra.CookieUtil;
import com.project.catxi.common.auth.infra.RefreshTokenRepository;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.KakaoUtil;
import com.project.catxi.common.auth.kakao.TokenDTO;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.common.jwt.JwtTokenProvider;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService {

  private final KakaoUtil kakaoUtil;
  private final JwtUtil jwtUtil;
  private final JwtTokenProvider jwtTokenProvider;
  private final MemberRepository memberRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final CodeCache codeCache;

  public Member processKakaoLogin(String accessCode, HttpServletResponse response) {

    // 중복 코드 차단
    if (codeCache.isDuplicate(accessCode)) {
      log.warn("🚨중복 code 요청 차단 code = {}", accessCode);
      throw new CatxiException(MemberErrorCode.DUPLICATE_AUTHORIZE_CODE);  }
    try {
      return oAuthLogin(accessCode, response);  }
    catch (Exception e) {
      log.error("[카카오 로그인 실패] code = {}, error = {}", accessCode, e.getMessage());
      codeCache.remove(accessCode);
      throw new CatxiException(MemberErrorCode.ACCESS_EXPIRED);  }
  }

  public Member oAuthLogin(String accessCode, HttpServletResponse response) {

    //카카오 토큰 요청
    KakaoDTO.kakaoToken kakaoToken = kakaoUtil.requestToken(accessCode);
    //사용자 정보 요청
    KakaoDTO.KakaoProfile kakaoProfile = kakaoUtil.requestProfile(kakaoToken);
    
    String email = kakaoProfile.kakao_account().email();
    Member user = memberRepository.findByEmail(email)
        .orElseGet(() -> createNewUser(kakaoProfile));

    validateNewUser(user);
    
    //JWT 발급 후 응답 헤더에 추가
    String jwt = loginProcess(response, user);
    
    //분기처리
    setNewUser(response, user);
    
    log.info("[카카오 프로필] email = {}", email);
    log.info("✅JWT 발급 : {}", jwt);

    return user;
  }

  private Member createNewUser(KakaoDTO.KakaoProfile kakaoProfile) {
    String email = kakaoProfile.kakao_account().email();
    String name = kakaoProfile.kakao_account().profile().nickname();

    log.info(">> name: {}", name);
    log.info(">> email: {}", email);

    Member newUser = Member.builder()
        .email(email)
        .membername(name)
        .password("NO_PASSWORD")
        .matchCount(0)
        .role("ROLE_USER")
        .status(MemberStatus.PENDING)
        .build();

    return memberRepository.save(newUser);
  }

  private String loginProcess(HttpServletResponse response, Member user) {
    String email = user.getEmail();

    String accessToken = jwtTokenProvider.generateAccessToken(email);
    response.setHeader("access", accessToken);
    
    String refreshToken = jwtTokenProvider.generateRefreshToken(email);
    refreshTokenRepository.save(email, refreshToken, Duration.ofDays(30));
    
    ResponseCookie refreshCookie = CookieUtil.createCookie(refreshToken, Duration.ofDays(30));
    response.setHeader("Set-Cookie", refreshCookie.toString());
    
    log.info("✅ [헤더에 담은 JWT] access = {}", accessToken);
    log.info("✅ [쿠키에 담은 RefreshToken] refresh = {}", refreshToken);

    return accessToken;
  }

  @Transactional
  public void catxiSignup(String email, KakaoDTO.CatxiSignUp dto) {
    Member member = memberRepository.findByEmail(email)
        .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

    validateCatxiSignUp(dto);
    
    member.setNickname(dto.nickname());
    member.setStudentNo(dto.StudentNo());
    member.setStatus(MemberStatus.ACTIVE);
    
  }
  
  public boolean isNNDuplicate(String nickname) {
    return memberRepository.existsByNickname(nickname);
  }

  //reissue
  public TokenDTO.Response reissueAccessToken(String refreshToken, HttpServletResponse response) {
    validateRefreshToken(refreshToken);
    
    //파싱 후 이메일 추출
    Claims claims = jwtUtil.parseJwt(refreshToken);
    String email = jwtUtil.getEmail(claims);

    //레디스에 저장된 토큰 값 비교
    if (!refreshTokenRepository.isValid(email, refreshToken)) {
      refreshTokenRepository.delete(email);
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_MISMATCH);
    }

    //토큰 생성
    String newAccessToken = jwtTokenProvider.generateAccessToken(email);
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

    //Token Rotate
    refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, Duration.ofDays(30));

    //RefreshToken 전송
    ResponseCookie refreshCookie = CookieUtil.createCookie(newRefreshToken, Duration.ofDays(30));
    response.setHeader("Set-Cookie", refreshCookie.toString());
    
    log.info("✅Rotate 이전 RT 값 : {}", refreshToken);
    log.info("🚨Rotate 이후 RT 값 : {}", newRefreshToken);

    return new TokenDTO.Response(newAccessToken, newRefreshToken);
  }

  //로그아웃
  public void logout(String refreshToken, HttpServletResponse response) {
    try {
      if (refreshToken != null && !refreshToken.isBlank() && jwtUtil.validateToken(refreshToken)) {
        refreshTokenRepository.deleteByToken(refreshToken);
      }
    } catch (Exception e) {
      log.warn("🚨로그아웃 실패: {}", e.getMessage());
    } finally {
      response.addHeader("Set-Cookie", CookieUtil.deleteCookie().toString());
    }
  }

  //회원 Status 점검
  private void validateNewUser(Member user) {
    log.info("🚨회원 Status = {}", user.getStatus());
    if (user.getStatus() == MemberStatus.INACTIVE) {
      throw new MemberHandler(MemberErrorCode.ACCESS_FORBIDDEN);
    }
  }

  //헤더에 setNewUser
  private void setNewUser(HttpServletResponse response, Member user) {
    boolean isNewUser = user.getStatus() == MemberStatus.PENDING;
    response.setHeader("isNewUser", String.valueOf(isNewUser));
  }

  //
  private void validateCatxiSignUp(KakaoDTO.CatxiSignUp dto) {
    if (memberRepository.existsByStudentNo(dto.StudentNo())) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
    }
    if (dto.nickname() == null || dto.nickname().length() > 9) {
      throw new CatxiException(MemberErrorCode.INVALID_NICKNAME_LENGTH);
    }
    if (memberRepository.existsByNickname(dto.nickname())) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
    }
    if (dto.StudentNo() == null || !dto.StudentNo().matches("\\d{9}")) {
      throw new CatxiException(MemberErrorCode.INVALID_STUDENT_NO);
    }
  }

  private void validateRefreshToken(String refreshToken) {
    log.info("🔍 [validateRefreshToken] 받은 토큰: {}", refreshToken);
    if (refreshToken == null || refreshToken.trim().isEmpty()) {
      log.warn("🚨 RefreshToken이 null이거나 비어있음");
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }
    if (!jwtUtil.validateToken(refreshToken)) {
      log.warn("🚨 RefreshToken 유효성 검사 실패");
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_EXPIRED);
    }
    log.info("✅ RefreshToken 유효성 검사 통과");
  }

}
