package com.project.catxi.common.auth.kakao;

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
  public record KakaoProfile(
      Long id,
      String connected_at,
      Properties properties,
      KakaoAccount kakao_account
  ) {
    public record Properties(
        String nickname
    ) {}

    public record KakaoAccount(
        String email,
        Profile profile
    ) {
      public record Profile(
          String nickname,
          String profile_image_url
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
