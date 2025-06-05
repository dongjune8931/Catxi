package com.project.catxi.common.auth.kakao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

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

    ObjectMapper objectMapper = new ObjectMapper();

    //응답받은 JSON KakaoDTO.kakaoToken 클래스에 매핑
    KakaoDTO.kakaoToken kakaoToken = null;

    try {
      kakaoToken = objectMapper.readValue(response.getBody(), KakaoDTO.kakaoToken.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid access token");
    }
    return kakaoToken;
  }

  public KakaoDTO.KakaoProfile requestProfile(KakaoDTO.kakaoToken kakaoToken){
    RestTemplate restTemplate2 = new RestTemplate();
    HttpHeaders headers2 = new HttpHeaders();

    ObjectMapper objectMapper = new ObjectMapper();

    headers2.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");
    headers2.add("Authorization", "Bearer " + kakaoToken.access_token());

    HttpEntity<MultiValueMap<String,String>> kakaoProfileRequest = new HttpEntity<>(headers2);

    //GET 요청으로 프로필 받아오기 위함
    ResponseEntity<String> response2 = restTemplate2.exchange(
        "https://kapi.kakao.com/v2/user/me",
        HttpMethod.GET,
        kakaoProfileRequest,
        String.class);

    //응답 JSON KakaoDTO.kakaoProfile에 매핑 -> nickname + 프사(이건 필요한지 모르겠음)
    KakaoDTO.KakaoProfile kakaoProfile = null;

    try {
      kakaoProfile = objectMapper.readValue(response2.getBody(), KakaoDTO.KakaoProfile.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid profile token");
    }

    return kakaoProfile;
  }

}
