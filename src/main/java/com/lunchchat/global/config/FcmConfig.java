package com.lunchchat.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FcmConfig {

    @Value("${fcm.service-account-file:}")
    private String serviceAccountFilePath;

    private FirebaseApp firebaseApp;

    @PostConstruct
    public void initialize() {
        try {
            if (serviceAccountFilePath.isEmpty()) {
                log.warn("FCM 서비스 계정 파일 경로가 설정되지 않았습니다. FCM 기능을 사용할 수 없습니다.");
                return;
            }

            File file = new File(serviceAccountFilePath);
            if (!file.exists()) {
                log.error("FCM 서비스 계정 파일을 찾을 수 없습니다: {}", serviceAccountFilePath);
                return;
            }

            InputStream serviceAccountStream = Files.newInputStream(Paths.get(serviceAccountFilePath));

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                .build();

            if (FirebaseApp.getApps().isEmpty()) {
                this.firebaseApp = FirebaseApp.initializeApp(options);
                log.info("FirebaseApp 초기화 성공");
            } else {
                this.firebaseApp = FirebaseApp.getInstance();
            }
        } catch (Exception e) {
            log.error("FirebaseApp 초기화 실패: {}", e.getMessage());
            this.firebaseApp = null;
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (firebaseApp == null) {
            return null;
        }
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}