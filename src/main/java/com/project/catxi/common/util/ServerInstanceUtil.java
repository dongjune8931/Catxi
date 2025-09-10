package com.project.catxi.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Component
public class ServerInstanceUtil {
    
    private String serverInstanceId;
    private static final int SERVER_COUNT = 2; // 총 서버 수
    
    @PostConstruct
    public void init() {
        try {
            // 호스트명 기반으로 서버 ID 생성
            String hostname = InetAddress.getLocalHost().getHostName();
            this.serverInstanceId = hostname;
            log.info("서버 인스턴스 ID 초기화: {}", serverInstanceId);
        } catch (UnknownHostException e) {
            // 호스트명을 얻을 수 없는 경우 현재 시간 기반으로 생성
            this.serverInstanceId = "server-" + (System.currentTimeMillis() % 1000);
            log.warn("호스트명을 얻을 수 없어 임시 ID 생성: {}", serverInstanceId);
        }
    }
    
    /**
     * 현재 서버가 특정 Room ID의 FCM 처리를 담당하는지 확인
     */
    public boolean shouldProcessFcmForRoom(Long roomId) {
        if (roomId == null) {
            log.warn("FCM 처리 분기 - RoomId가 null입니다");
            return false;
        }
        
        // Room ID를 해시해서 서버 수로 나눈 나머지로 분기
        int serverIndex = Math.abs(roomId.hashCode()) % SERVER_COUNT;
        int currentServerIndex = getServerIndex();
        boolean shouldProcess = currentServerIndex == serverIndex;
        
        // INFO 레벨로 로그 출력하여 디버깅
        log.info("FCM 처리 분기 - RoomId: {}, RoomHash: {}, ServerIndex: {}, CurrentServerIndex: {}, ServerInstanceId: {}, ShouldProcess: {}", 
                roomId, roomId.hashCode(), serverIndex, currentServerIndex, serverInstanceId, shouldProcess);
        
        // 임시로 모든 서버에서 처리하도록 true 반환 (디버깅용)
        log.warn("임시 모드: 모든 서버에서 FCM 처리 허용");
        return true;
    }
    
    /**
     * 현재 서버의 인덱스 (0 또는 1)
     */
    private int getServerIndex() {
        // 서버 인스턴스 ID의 해시값으로 0 또는 1 결정
        return Math.abs(serverInstanceId.hashCode()) % SERVER_COUNT;
    }
    
    /**
     * 현재 서버 인스턴스 ID 반환
     */
    public String getServerInstanceId() {
        return serverInstanceId;
    }
}