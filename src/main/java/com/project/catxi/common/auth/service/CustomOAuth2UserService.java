package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.api.handler.MemberHandler;
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

  public Member oAuthLogin(String accessCode, HttpServletResponse response) {
    // 카카오 토큰 요청
    KakaoDTO.kakaoToken kakaoToken = kakaoUtil.requestToken(accessCode);
    // 사용자 정보 요청
    KakaoDTO.KakaoProfile kakaoProfile = kakaoUtil.requestProfile(kakaoToken);
    // 이메일로 기존 사용자 조회
    String requestEmail = kakaoProfile.kakao_account().email();
    Member user = memberRepository.findByEmail(requestEmail)
        .orElseGet(()->createNewUser(kakaoProfile));

    // 탈퇴한 회원 차단
    log.info("🚨회원 Status = {}",user.getStatus());
    if (user.getStatus() == MemberStatus.INACTIVE) {
      throw new MemberHandler(MemberErrorCode.ACCESS_FORBIDDEN);
    }

    // JWT 발급 후 응답 헤더에 추가
    String jwt = loginProcess(response, user);

    // /signUp/catxi로 분기
    boolean isNewUser = user.getStatus()==MemberStatus.PENDING;
    response.setHeader("isNewUser", String.valueOf(isNewUser));

    log.info("[카카오 프로필] email = {}", requestEmail);
    log.info("✅JWT 발급 : {}", jwt);

    return user;
  }

  private Member createNewUser(KakaoDTO.KakaoProfile kakaoProfile) {

    String email = kakaoProfile.kakao_account().email();
    // 멤버 이름(앱 내 닉네임 X), 동의항목에서 실명 제공 안해줌 Fuck you kakao
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
        .status(MemberStatus.PENDING)
        .build();

    return memberRepository.save(newUser);
  }

  private String loginProcess(HttpServletResponse httpServletResponse,Member user) {

    String email = user.getEmail();

    // 액세스 토큰 생성 및 헤더 설정
    String access = jwtTokenProvider.generateAccessToken(email);
    httpServletResponse.setHeader("access", access);
    log.info("✅ [헤더에 담은 JWT] access = {}", httpServletResponse.getHeader("access"));

    // 리프레시 토큰 생성 및 Redis 저장
    String refreshToken = jwtTokenProvider.generateRefreshToken(email);
    refreshTokenRepository.save(email, refreshToken, Duration.ofDays(30));
    
    // 리프레시 토큰 쿠키 설정
    ResponseCookie refreshCookie = CookieUtil.createCookie(refreshToken, Duration.ofDays(30));
    httpServletResponse.setHeader("Set-Cookie", refreshCookie.toString());
    log.info("✅ [쿠키에 담은 RefreshToken] refresh = {}", refreshToken);

    return access;
  }

  @Transactional
  public void catxiSignup(String email, KakaoDTO.CatxiSignUp dto) {
    Member member = memberRepository.findByEmail(email)
        .orElseThrow(() -> { return new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND);});

    if (memberRepository.existsByStudentNo(dto.StudentNo())) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
    }

    // 닉네임 길이 검증 (9자 이하)
    if (dto.nickname() == null || dto.nickname().length() > 9) {
      throw new CatxiException(MemberErrorCode.INVALID_NICKNAME_LENGTH);
    }

    // 닉네임 중복 체크
    if (memberRepository.existsByNickname(dto.nickname())) {
      throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
    }

    // 학번 검증 로직 (정확히 9자리 숫자)
    if (dto.StudentNo() == null || !dto.StudentNo().matches("\\d{9}")) {
      throw new CatxiException(MemberErrorCode.INVALID_STUDENT_NO);
    }

    member.setNickname(dto.nickname());
    member.setStudentNo(dto.StudentNo());
    member.setStatus(MemberStatus.ACTIVE);
  }

  public boolean isNNDuplicate(String nickname) {
        return memberRepository.existsByNickname(nickname);
  }

  // Reissue
  public TokenDTO.Response reissueAccessToken(String refreshToken, HttpServletResponse response) {

    // 1. 리프레시 토큰 검사
    if (refreshToken == null || refreshToken.trim().isEmpty()) {
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    // 2. 유효성 검사
    if (!jwtUtil.validateToken(refreshToken)) {
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_EXPIRED);
    }

    // 3. 파싱 후 이메일 추출
    Claims claims = jwtUtil.parseJwt(refreshToken);
    String email = jwtUtil.getEmail(claims);

    // 4. Redis RT와 비교
    if (!refreshTokenRepository.isValid(email, refreshToken)) {
      refreshTokenRepository.delete(email);
      throw new CatxiException(MemberErrorCode.REFRESH_TOKEN_MISMATCH);
    }

    log.info("✅Rotate 이전 RT 값 : {}", refreshToken);
    // 5. 토큰 생성
    String newAccessToken = jwtTokenProvider.generateAccessToken(email);
    String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

    // 6. 토큰 rotate
    refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, Duration.ofDays(30));

    // 7. RT 전송
    ResponseCookie refreshCookie = CookieUtil.createCookie(newRefreshToken, Duration.ofDays(30));
    response.setHeader("Set-Cookie", refreshCookie.toString());
    log.info("🚨Rotate 이후 RT 값 : {}", newRefreshToken);

    return new TokenDTO.Response(newAccessToken, newRefreshToken);
  }

  public void logout(String refreshToken, HttpServletResponse response) {
    //쿠키 값 확인
    if (refreshToken == null || refreshToken.isBlank()) {
      response.addHeader("Set-Cookie", CookieUtil.deleteCookie().toString());
      return;
    }

    //토큰 유효성 검사
    try {
      if (!jwtUtil.validateToken(refreshToken)) {
        response.addHeader("Set-Cookie", CookieUtil.deleteCookie().toString());
        return;
      }

      Claims claims = jwtUtil.parseJwt(refreshToken);
      String email = jwtUtil.getEmail(claims);

      // 요청이 온다면 일단 삭제(일치여부와 상관없이)
      if (refreshTokenRepository.isValid(email, refreshToken)) {
        refreshTokenRepository.deleteByToken(refreshToken);
      } else {
        refreshTokenRepository.deleteByToken(refreshToken);
      }
    } catch (Exception e) {
      log.warn("🚨로그아웃 실패: {}", e.getMessage());
    } finally {
      response.addHeader("Set-Cookie", CookieUtil.deleteCookie().toString());
    }
  }

}
