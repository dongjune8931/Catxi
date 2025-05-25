package com.project.catxi.common.jwt;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtill {

  private SecretKey secretKey;

  public JwtUtill(@Value("${SECRET_KEY}")String secret){
    // key는 객체 타입으로 저장 -> 키를 암호화
    secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), Jwts.SIG.HS256.key().build().getAlgorithm());
  }

  public String getMembername(String token) {

    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("membername", String.class);
  }

  //토큰 소멸 여부 확인
  public Boolean isExpired(String token) {

    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getExpiration().before(new Date());
  }

  public String createJwt(String Membername, String role, Long expiredMs) {

    return Jwts.builder()
        .claim("membername", getMembername(Membername))
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + expiredMs))
        .signWith(secretKey)
        .compact();
  }

}
