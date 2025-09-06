package com.project.catxi.common.auth.service;

import com.project.catxi.chat.repository.ChatMessageRepository;
import com.project.catxi.chat.repository.ChatParticipantRepository;
import com.project.catxi.chat.repository.ChatRoomRepository;
import com.project.catxi.chat.repository.KickedParticipantRepository;
import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.common.auth.infra.KakaoAccessTokenRepository;
import com.project.catxi.common.auth.infra.RefreshTokenRepository;
import com.project.catxi.common.auth.infra.TokenBlacklistRepository;
import com.project.catxi.common.auth.kakao.KakaoFeignClient;
import com.project.catxi.common.jwt.JwtUtil;
import com.project.catxi.member.domain.DeleteLog;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.DeleteLogRepository;
import com.project.catxi.member.repository.MatchHistoryRepository;
import com.project.catxi.member.repository.MemberRepository;
import com.project.catxi.report.repository.ReportRepository;
import feign.FeignException;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BlacklistService {

    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;
    private final KakaoAccessTokenRepository kakaoAccessTokenRepository;
    private final KakaoFeignClient kakaoFeignClient;
    private final MatchHistoryRepository matchHistoryRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final KickedParticipantRepository kickedParticipantRepository;
    private final ReportRepository reportRepository;
    private final DeleteLogRepository deleteLogRepository;

    // 사용자를 블랙리스트에 추가 (영구)
    @Transactional
    public void addUserToBlacklistPermanent(Long userId) {
        Member member = memberRepository.findById(userId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        
        // 사용자를 블랙리스트에 추가
        tokenBlacklistRepository.addUserToBlacklist(userId.toString());
        
        // 해당 사용자의 refreshToken을 Redis에서 삭제
        refreshTokenRepository.delete(member.getEmail());
        
        log.info("✅ 유저 블랙리스트 추가 및 RefreshToken 삭제: {} ({})", member.getEmail(), userId);
    }

    // 사용자를 블랙리스트에서 제거
    @Transactional
    public void removeUserFromBlacklist(Long userId) {
        Member member = memberRepository.findById(userId)
            .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));
        
        tokenBlacklistRepository.removeUserFromBlacklist(userId.toString());
        log.info("✅ 유저 블랙리스트 해제: {} ({})", member.getEmail(), userId);
    }

    // 회원 탈퇴
    @Transactional
    public void resignation(String email, String accessToken) {
        try {
            // 1. JWT 검증 & 사용자 식별
            if (!jwtUtil.validateToken(accessToken)) {
                throw new CatxiException(MemberErrorCode.INVALID_TOKEN);
            }

            Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));


            // 2. 카카오 연결 해제
            boolean kakaoUnlinked = unlinkKakaoAccount(email);
            if (!kakaoUnlinked) {
                log.error("❌ 카카오 연결 해제 실패: {}", email);
                // 카카오 연결 해제 실패시 회원탈퇴 중지
                throw new CatxiException(MemberErrorCode.KAKAO_UNLINK_FAILED);
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
                log.error("❌ 카카오 액세스 토큰을 찾을 수 없음: {}", email);
                return false;
            }

            // 카카오 연결 해제 API 호출
            kakaoFeignClient.unlinkUser("Bearer " + kakaoAccessToken);

            // 성공 시에만 토큰 삭제
            kakaoAccessTokenRepository.delete(email);
            log.info("✅ 카카오 연결 해제 및 토큰 삭제 완료: {}", email);
            return true;

        } catch (FeignException e) {
            log.error("❌ 카카오 API 연결 해제 실패 - Status: {}, Response: {}", e.status(), e.contentUTF8());
            return false;
        } catch (Exception e) {
            log.error("❌ 카카오 연결 해제 처리 중 예외 발생: {}", e.getMessage(), e);
            return false;
        }
    }

    //DB 정리
    private void dropMemberData(Member member, String accessToken, String email) {
        try {
            //1.현재 AccessToken 블랙리스트 등록
            addAccessTokenToBlacklist(accessToken);

            //2.사용자 영구 블랙리스트 등록 (모든 토큰 차단)
            tokenBlacklistRepository.addUserToBlacklist(member.getId().toString());

            //3.RefreshToken 삭제
            refreshTokenRepository.delete(email);

            //4.멤버 삭제 전 로그화
            deleteLogRepository.save(DeleteLog.builder()
                .deletedEmail(email)
                .memberId(member.getId())
                .createdAt(LocalDateTime.now())
                .build());

            //5.연관 엔티티들 삭제
            reportRepository.deleteAllByReporter(member);
            reportRepository.deleteAllByReportedMember(member);
            matchHistoryRepository.deleteAllByUser(member);
            chatParticipantRepository.deleteAllByMember(member);
            kickedParticipantRepository.deleteAllByMember(member);
            chatMessageRepository.deleteAllByMember(member);
            chatRoomRepository.deleteAllByHost(member);

            //최종 회원 삭제
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


    // 사용자가 블랙리스트에 있는지 확인
    public boolean isUserBlacklisted(Long userId) {
        return tokenBlacklistRepository.isUserBlacklisted(userId.toString());
    }
}