package com.project.catxi.common.auth.service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.auth.infra.CookieUtil;
import com.project.catxi.common.auth.infra.RefreshTokenRepository;
import com.project.catxi.common.auth.infra.TokenBlacklistRepository;
import com.project.catxi.common.auth.infra.KakaoAccessTokenRepository;
import com.project.catxi.common.auth.kakao.KakaoDTO;
import com.project.catxi.common.auth.kakao.KakaoFeignClient;
import com.project.catxi.common.auth.kakao.TokenDTO;
import com.project.catxi.common.domain.MemberStatus;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.common.jwt.JwtTokenProvider;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.member.repository.MatchHistoryRepository;
import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.KickedParticipantRepository;
import com.project.catxi.report.repository.ReportRepository;
import feign.FeignException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final JwtUtil jwtUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final KakaoAccessTokenRepository kakaoAccessTokenRepository;
    private final KakaoFeignClient kakaoFeignClient;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final KickedParticipantRepository kickedParticipantRepository;
    private final ReportRepository reportRepository;


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

        //사용자 정보 조회하여 role 가져오기
        Member member = memberRepository.findByEmail(email)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        
        //토큰 생성
        String newAccessToken = jwtTokenProvider.generateAccessToken(email, member.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email, member.getRole());

        //Token Rotate
        refreshTokenRepository.rotate(email, refreshToken, newRefreshToken, Duration.ofDays(30));

        //RefreshToken 전송
        ResponseCookie refreshCookie = CookieUtil.createCookie(newRefreshToken, Duration.ofDays(30));
        response.setHeader("Set-Cookie", refreshCookie.toString());
        
        log.info("✅Rotate 이전 RT 값 : {}", refreshToken);
        log.info("🚨Rotate 이후 RT 값 : {}", newRefreshToken);

        return new TokenDTO.Response(newAccessToken, newRefreshToken);
    }

    //로그아웃 (AccessToken 블랙리스트 추가)
    public void logout(HttpServletRequest request, String refreshToken, HttpServletResponse response) {
        try {
            // AccessToken 블랙리스트에 추가
            String authorization = request.getHeader("Authorization");

            if (authorization != null && authorization.startsWith("Bearer ")) {
                String accessToken = authorization.substring("Bearer ".length());
                //토큰 유효성 검사
                if (jwtUtil.validateToken(accessToken)) {
                    // 토큰 유효기간 계산
                    Claims claims = jwtUtil.parseJwt(accessToken);
                    Date expiration = claims.getExpiration();
                    long remainTime = expiration.getTime() - System.currentTimeMillis();
                    
                    if (remainTime > 0) {
                        // 블랙리스트에 추가
                        tokenBlacklistRepository.addTokenToBlacklist(accessToken, Duration.ofMillis(remainTime));
                        
                        // 해당 사용자의 모든 refreshToken도 삭제 (보안 강화)
                        String email = jwtUtil.getEmail(claims);
                        refreshTokenRepository.delete(email);
                        
                        log.info("✅ AccessToken 블랙리스트 등록 및 RefreshToken 삭제: {}", email);
                    }
                }
            }

            if (refreshToken != null && !refreshToken.isBlank() && jwtUtil.validateToken(refreshToken)) {
                //RefreshToken Redis에서 삭제
                refreshTokenRepository.deleteByToken(refreshToken);
                log.info("✅ RefreshToken 삭제 완료");
            }
        } catch (Exception e) {
            log.warn("🚨로그아웃 중 오류 발생: {}", e.getMessage());
        } finally {
            response.addHeader("Set-Cookie", CookieUtil.deleteCookie().toString());
        }
    }
    
    //2차 회원가입
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

    public void resignation(String accessToken) {
        try {
            // 1. JWT 검증 & 사용자 식별
            if (!jwtUtil.validateToken(accessToken)) {
                throw new CatxiException(MemberErrorCode.INVALID_TOKEN);
            }

            Claims claims = jwtUtil.parseJwt(accessToken);
            String email = jwtUtil.getEmail(claims);
            Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

            // 2. 카카오 연결 해제 시도 (실패해도 회원 탈퇴는 진행)
            boolean kakaoUnlinked = unlinkKakaoAccount(email);

            if (!kakaoUnlinked) {
                log.warn("⚠️ 카카오 연결 해제 실패, 회원 탈퇴만 진행: {}", email);
            }

            // 3. DB 정리
            dropMemberData(member, accessToken, email);
            
        } catch (CatxiException e) {
            log.error("❌ 회원 탈퇴 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ 회원 탈퇴 처리 오류 발생: {}", e.getMessage());
            throw new CatxiException(MemberErrorCode.WITHDRAWAL_FAILED);
        }
    }

    //카카오 연결 해제
    private boolean unlinkKakaoAccount(String email) {
        try {
            String kakaoAccessToken = kakaoAccessTokenRepository.findByEmail(email)
                .orElse(null);
                
            if (kakaoAccessToken == null) {
                log.warn("⚠️ 카카오 액세스 토큰을 찾을 수 없음: {}", email);
                return false;
            }

            kakaoFeignClient.unlinkUser("Bearer " + kakaoAccessToken);
            kakaoAccessTokenRepository.delete(email);
            log.info("✅ 카카오 연결 해제 및 토큰 삭제 완료: {}", email);
            return true;

        } catch (FeignException e) {
            log.error("❌ 카카오 연결 해제 실패: {}", e.contentUTF8());
            return false;
        } catch (Exception e) {
            log.error("❌ 카카오 연결 해제 처리 중 오류: {}", e.getMessage());
            return false;
        }
    }

    //DB 정리
    @Transactional
    protected void dropMemberData(Member member, String accessToken, String email) {
        try {
            // 1. AccessToken 블랙리스트 등록
            addAccessTokenToBlacklist(accessToken);
            
            // 2. RefreshToken 삭제
            refreshTokenRepository.delete(email);

            // 3. 연관 엔티티들 삭제
            
            // 3-1. 신고 기록 삭제 (reporter, reportedMember)
            reportRepository.deleteAllByReporter(member);
            reportRepository.deleteAllByReportedMember(member);
            
            // 3-2. 매치 히스토리 삭제
            matchHistoryRepository.deleteAllByUser(member);
            
            // 3-3. 채팅 참가자 기록 삭제
            chatParticipantRepository.deleteAllByMember(member);
            
            // 3-4. 강퇴된 참가자 기록 삭제
            kickedParticipantRepository.deleteAllByMember(member);
            
            // 3-5. 채팅 메시지 삭제
            chatMessageRepository.deleteAllByMember(member);
            
            // 3-6. 호스트로 생성한 채팅룸 삭제
            chatRoomRepository.deleteAllByHost(member);

            // 4. 최종 회원 삭제 (Hard Delete)
            memberRepository.delete(member);
            log.info("✅ 회원 DB삭제 완료: {}", email);

        } catch (Exception e) {
            log.error("❌ 회원 데이터 정리 실패: {}", e.getMessage());
            throw new CatxiException(MemberErrorCode.WITHDRAWAL_FAILED);
        }
    }

    //남은 액세스 토큰 블랙리스트 등록(연결 차단)
    private void addAccessTokenToBlacklist(String accessToken) {
        Claims claims = jwtUtil.parseJwt(accessToken);
        Date expiration = claims.getExpiration();
        long remainTime = expiration.getTime() - System.currentTimeMillis();
        
        if (remainTime > 0) {
            tokenBlacklistRepository.addTokenToBlacklist(accessToken, Duration.ofMillis(remainTime));
        }
    }

    //리프레시토큰 검증
    private void validateRefreshToken(String refreshToken) {

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
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
        //닉네임 제한
        if (dto.nickname() == null || dto.nickname().length() > 9) {
            throw new CatxiException(MemberErrorCode.INVALID_NICKNAME_LENGTH);
        }
        //닉네임 중복체크
        if (memberRepository.existsByNickname(dto.nickname())) {
            throw new CatxiException(MemberErrorCode.DUPLICATE_NICKNAME);
        }
        //학번 정확히 9글자 숫자
        if (dto.StudentNo() == null || !dto.StudentNo().matches("\\d{9}")) {
            throw new CatxiException(MemberErrorCode.INVALID_STUDENT_NO);
        }
        //학번 중복체크
        if (memberRepository.existsByStudentNo(dto.StudentNo())) {
            throw new CatxiException(MemberErrorCode.DUPLICATE_STUDENT_NO);
        }
    }
}