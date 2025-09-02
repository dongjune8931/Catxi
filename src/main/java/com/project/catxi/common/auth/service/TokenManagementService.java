package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.auth.infra.CookieUtil;
import com.project.catxi.common.auth.infra.RefreshTokenRepository;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.TokenDTO;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.common.jwt.JwtTokenProvider;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenManagementService {

    private final JwtUtil jwtUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

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

    //리프레시토큰 검증
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

    //회원가입 검증
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
}