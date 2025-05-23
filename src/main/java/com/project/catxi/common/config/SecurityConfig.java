package com.project.catxi.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  private final AuthenticationConfiguration authenticationConfiguration;

  // 객체 생성자 주입
  public SecurityConfig(AuthenticationConfiguration authenticationConfiguration) {
    this.authenticationConfiguration = authenticationConfiguration;
  }

  // Authentication Manager Bean 등록
  public AuthenticationManager authenticationMananager(AuthenticationConfiguration configuration) throws Exception{
    return configuration.getAuthenticationManager();
  }

  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    //csrf disable
    //JWT의 경우 세션을 stateless로 운영 -> csrf 공격에 대해 깊이 생각할 필요 X
    http
        .csrf((auth) -> auth.disable());

    //Form 로그인 방식 disable -> Custom하게 설정
    http
        .formLogin((auth) -> auth.disable());


    //http basic 인증 방식 disable
    http
        .httpBasic((auth) -> auth.disable());

    //경로별 인가 작업
    http
        .authorizeHttpRequests((auth)-> auth
            .requestMatchers("/login","/","/join").permitAll()
            .requestMatchers("/admin").hasRole("ADMIN")
            .anyRequest().authenticated()
        );

    //Filter 등록 매개변수(필터 , 위치)
    //addFilterAt : 원하는 자리에 등록 , before: 해당 필터 전, after: 해당 필터 이후
    //http
    //    .addFilterAt(new LoginFilter(authenticationManager(authenticationConfiguration)), UsernamePasswordAuthenticationFilter.class);

    // 세션 설정
    // JWT -> Session 항상 Stateless 상태로 둬야 함
    http
        .sessionManagement((session) -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

    return http.build();
  }


}
