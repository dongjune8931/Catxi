package com.project.catxi.common.auth.kakao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
@Slf4j
public class KakaoUtill {

  @Value("${spring.security.oauth2.client.registration.kakao.client-id}")
  private String client;
  @Value("${spring.security.oauth2.client.registration.kakao.redirect-uri}")
  private String redirect;

  // 인가 코드 -> accessToken 요청
  public KakaoDTO.kakaoToken requestToken(String accessCode) {
    //HTTP 요청용
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();

    log.info(">> [🚨RestTemplate 컨트롤러 호출] time = {}, code = {}", LocalDateTime.now(), accessCode);

    headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

    log.info(">> Used Redirect URI : " + redirect);

    // 인가코드, 카카오 REST_API키, redirect_uri,카카오 제공 인가 코드 요청하기 위한 파라미터
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("client_id", client);
    params.add("redirect_uri", redirect);
    params.add("code", accessCode);

    HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

    //accessToken 요청
    ResponseEntity<String> response = restTemplate.exchange(
        "https://kauth.kakao.com/oauth/token",
        HttpMethod.POST,
        kakaoTokenRequest,
        String.class);

    log.info(">> [Token Request Params]");
    params.forEach((k, v) -> log.info("{} = {}", k, v));

    ObjectMapper objectMapper = new ObjectMapper();

    //응답받은 JSON KakaoDTO.kakaoToken 클래스에 매핑
    KakaoDTO.kakaoToken kakaoToken = null;

    try {
      kakaoToken = objectMapper.readValue(response.getBody(), KakaoDTO.kakaoToken.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid access token");
    }

    log.warn("[🚨Fuck 중복 시도] accessCode = {}", accessCode);

    return kakaoToken;
  }

  public KakaoDTO.KakaoProfile requestProfile(KakaoDTO.kakaoToken kakaoToken){
    RestTemplate restTemplate2 = new RestTemplate();
    HttpHeaders headers2 = new HttpHeaders();

    headers2.setBearerAuth(kakaoToken.access_token());

    HttpEntity<Void> kakaoProfileRequest = new HttpEntity<>(headers2);

    //GET 요청으로 프로필 받아오기 위함
    ResponseEntity<String> response2 = restTemplate2.exchange(
        "https://kapi.kakao.com/v2/user/me",
        HttpMethod.GET,
        kakaoProfileRequest,
        String.class);

    System.out.println("Kakao Profile Raw Response: " + response2.getBody());

    ObjectMapper objectMapper = new ObjectMapper();

    try {
      return objectMapper.readValue(response2.getBody(), KakaoDTO.KakaoProfile.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid profile token");
    }

  }

}
