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

  public String createJwt(String type,String email, String role, Long expiredMs) {

    return Jwts.builder()
        //이메일을 주 키로 사용하기 위함
        .setSubject(email)
        .claim("type", type) //TokenType
        .claim("email", email)
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

  public String getEmail(String token) {
    return parseJwt(token).get("email", String.class);
  }

  public void isExpired(String token) throws ExpiredJwtException {
    parseJwt(token);
  }

  public boolean validateToken(String token) {
    try {
      Claims claims = parseJwt(token);
      return !claims.getExpiration().before(new Date());
    } catch (Exception e) {
      return false;
    }
  }

  public boolean isRefreshToken(String token) {
    try {
      return "refresh".equals(parseJwt(token).get("type", String.class));
    } catch (Exception e) {
      return false;
    }
  }

}
