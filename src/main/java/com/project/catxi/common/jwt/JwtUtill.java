package com.project.catxi.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
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

  public String createJwt(String type, String membername, String role, Long expiredMs) {

    return Jwts.builder()
        .claim("type", type) //TokenType
        .claim("membername", membername)
        .claim("role", role)
        .issuedAt(new Date(System.currentTimeMillis()))
        .expiration(new Date(System.currentTimeMillis() + expiredMs*1000))
        .signWith(secretKey)
        .compact();
  }

  private Claims parseJwt(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public String getCategory(String token) {
    return parseJwt(token).get("type", String.class);
  }

  public String getMembername(String token) {
    return parseJwt(token).get("membername", String.class);
  }

  public void isExpired(String token) throws ExpiredJwtException {
    parseJwt(token);
  }

}
