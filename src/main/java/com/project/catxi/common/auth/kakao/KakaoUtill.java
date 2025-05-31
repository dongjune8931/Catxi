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

  @Value("${spring.kakao.auth.client}")
  private String client;
  @Value("${spring.kakao.auth.redirect}")
  private String redirect;

  public KakaoDTO.kakaoToken requestToken(String accesscode) {
    RestTemplate restTemplate = new RestTemplate();
    HttpHeaders headers = new HttpHeaders();

    headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.add("grant_type", "authorization_code");
    params.add("client_id", client);
    params.add("redirect_url", redirect);
    params.add("code", accesscode);

    HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);

    ResponseEntity<String> response = restTemplate.exchange(
        "https://kauth.kakao.com/oauth/token",
        HttpMethod.POST,
        kakaoTokenRequest,
        String.class);

    ObjectMapper objectMapper = new ObjectMapper();

    KakaoDTO.kakaoToken kakaoToken = null;

    try {
      kakaoToken = objectMapper.readValue(response.getBody(), KakaoDTO.kakaoToken.class);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid access token");
    }
    return kakaoToken;
  }

}
