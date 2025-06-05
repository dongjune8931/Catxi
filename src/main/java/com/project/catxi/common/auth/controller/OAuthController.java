package com.project.catxi.common.auth.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.auth.service.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class OAuthController {

  private final CustomOAuth2UserService customOAuth2UserService;

  @GetMapping("/login/kakao")
  public ApiResponse<?> kakaoLogin(@RequestParam("code") String accessCode, HttpServletResponse response) {
    customOAuth2UserService.oAuthLogin(accessCode, response);

    return ApiResponse.success("로그인 성공");
  }

}
