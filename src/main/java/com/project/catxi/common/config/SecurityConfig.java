package com.project.catxi.common.config;

import com.project.catxi.common.jwt.JwtFilter;
import com.project.catxi.common.jwt.JwtUtill;
import com.project.catxi.common.jwt.LoginFilter;
import com.project.catxi.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtUtill jwtUtill;
  private final AuthenticationConfiguration authenticationConfiguration;
  private final JwtConfig jwtConfig;

  private final MemberRepository memberRepository;

  // Authentication Manager Bean 등록 -> UsernamePasswordAuthenticationFilter에서 필요
  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception{
    return authenticationConfiguration.getAuthenticationManager();
  }

  @Bean
  public BCryptPasswordEncoder bCryptPasswordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtConfig jwtConfig) throws Exception {

    //csrf disable
    http
        .csrf((auth) -> auth.disable());

    //임시 CORS 설정
    http
        .cors(
            cors -> cors.configurationSource(corsConfigurationSource())
        );

    //Form 로그인 방식 disable -> Custom하게 설정
    http
        .formLogin((auth) -> auth.disable());

    //http basic 인증 방식 disable
    http
        .httpBasic((auth) -> auth.disable());

    //경로별 인가 작업
    http
        .authorizeHttpRequests((auth)-> auth
            .requestMatchers("/swagger", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll() // Swagger 허용
            .requestMatchers("/login","/","/signUp").permitAll()
            .requestMatchers("/auth/login/kakao").permitAll()
            .requestMatchers("/actuator/**").permitAll()
            .requestMatchers("/admin").hasRole("ADMIN")
            .anyRequest().authenticated()
        );

    // 로그인 필터 이전에 동작시킴
    http
        .addFilterBefore(new JwtFilter(jwtUtill,jwtConfig), LoginFilter.class);

    //Filter 등록 매개변수(필터 , 위치)
    //addFilterAt : 원하는 자리에 등록 , before: 해당 필터 전, after: 해당 필터 이후
    http
        .addFilterBefore((new LoginFilter(authenticationManager(authenticationConfiguration), jwtUtill, jwtConfig,memberRepository)), UsernamePasswordAuthenticationFilter.class);

    // 세션 설정
    // JWT -> Session 항상 Stateless 상태로 둬야 함
    http
        .sessionManagement((session) -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

    return http.build();
  }

  //모든 경로 대해 cors 요청 허용
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.addAllowedOriginPattern("*");

    configuration.addAllowedHeader("*");
    configuration.addAllowedMethod("*");

    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;

  }


}
