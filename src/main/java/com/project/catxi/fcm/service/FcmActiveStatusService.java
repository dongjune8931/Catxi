package com.project.catxi.fcm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.project.catxi.common.api.error.MemberErrorCode;
import com.project.catxi.common.api.exception.CatxiException;
import com.project.catxi.member.domain.Member;
import com.project.catxi.member.repository.MemberRepository;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FcmActiveStatusService {

    private final @Qualifier("chatPubSub") StringRedisTemplate redisTemplate;
    private final MemberRepository memberRepository;
    
    private static final String ACTIVE_STATUS_KEY_PREFIX = "chat:active:room:%d:user:%d";
    private static final int ACTIVE_STATUS_TTL_MINUTES = 5; // 5분 TTL

    /**
     * 사용자 활성 상태 업데이트
     *
     * @param email 사용자 이메일
     * @param roomId 채팅방 ID
     * @param isActive 활성 상태
     */
    public void updateUserActiveStatus(String email, Long roomId, boolean isActive) {
        try {
            Member member = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new CatxiException(MemberErrorCode.MEMBER_NOT_FOUND));

            String key = String.format(ACTIVE_STATUS_KEY_PREFIX, roomId, member.getId());

            if (isActive) {
                // 활성 상태로 설정 (TTL 5분)
                redisTemplate.opsForValue().set(key, "true", ACTIVE_STATUS_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("사용자 활성 상태 설정 - MemberId: {}, RoomId: {}", member.getId(), roomId);
            } else {
                // 비활성 상태로 설정 (키 삭제)
                redisTemplate.delete(key);
                log.debug("사용자 비활성 상태 설정 - MemberId: {}, RoomId: {}", member.getId(), roomId);
            }

        } catch (Exception e) {
            log.error("사용자 활성 상태 업데이트 실패 - Email: {}, RoomId: {}, Active: {}, Error: {}", 
                    email, roomId, isActive, e.getMessage(), e);
            // 활성 상태 업데이트 실패가 다른 기능에 영향을 주지 않도록 예외를 던지지 않음
        }
    }

    /**
     * 사용자가 채팅방에서 활성 상태인지 확인
     *
     * @param memberId 멤버 ID
     * @param roomId 채팅방 ID
     * @return 활성 상태 여부
     */
    public boolean isUserActiveInRoom(Long memberId, Long roomId) {
        try {
            String key = String.format(ACTIVE_STATUS_KEY_PREFIX, roomId, memberId);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("사용자 활성 상태 확인 실패 - MemberId: {}, RoomId: {}, Error: {}", 
                    memberId, roomId, e.getMessage(), e);
            // 확인 실패 시 기본적으로 비활성으로 처리
            return false;
        }
    }

}