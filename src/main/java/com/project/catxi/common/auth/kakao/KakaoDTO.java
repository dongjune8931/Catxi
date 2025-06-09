package com.project.catxi.common.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Properties;
import lombok.Getter;

public class KakaoDTO {

  //카카오 액세스 토큰
  public record kakaoToken(
      String access_token,
      String token_type,
      String refresh_token,
      int expires_in,
      String scope,
      int refresh_token_expires_in
  ){}

  //카카오 사용자 정보 응답
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KakaoProfile(
      Long id,
      String connected_at,
      KakaoAccount kakao_account
  ) {
    //카카오 계정에서 추출할 내용
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(
        String email,
        Profile profile
    ) {
      @JsonIgnoreProperties(ignoreUnknown = true)
      public record Profile(
          String nickname
      ) {}
    }
  }

  //서비스용
  public record KakaoUser(
      Long id,
      String email,
      String nickname
  ) {}

}
