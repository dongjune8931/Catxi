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
            return false;
        }
        
        // Room ID를 해시해서 서버 수로 나눈 나머지로 분기
        int serverIndex = Math.abs(roomId.hashCode()) % SERVER_COUNT;
        boolean shouldProcess = getServerIndex() == serverIndex;
        
        log.debug("FCM 처리 분기 - RoomId: {}, ServerIndex: {}, CurrentServer: {}, ShouldProcess: {}", 
                roomId, serverIndex, getServerIndex(), shouldProcess);
        
        return shouldProcess;
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