package com.project.catxi.member.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.common.config.JwtConfig;
import com.project.catxi.common.jwt.JwtUtill;
import com.project.catxi.member.DTO.AuthDTO;
import com.project.catxi.member.DTO.AuthDTO.LoginResponse;
import com.project.catxi.member.DTO.IdResponse;
import com.project.catxi.member.DTO.SignUpDTO;
import com.project.catxi.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.GrantedAuthority;


@RestController
@RequiredArgsConstructor
public class MemberController {

  private final MemberService memberService;
  private final JwtUtill jwtUtill;
  private final AuthenticationManager authenticationManager;
  private final JwtConfig jwtConfig;

  //@Operation(summary = "SignUp Api")
  @PostMapping("/signUp")
  public ResponseEntity<IdResponse> signUp(@RequestBody @Valid SignUpDTO dto) {
    Long id = memberService.signUp(dto);
    return ResponseEntity
        .created(URI.create("/api/members/" + id))
        .body(new IdResponse(id));
  }

  @Operation(summary = "Login API")
  @PostMapping("/login")
  public ResponseEntity<LoginResponse> login(@RequestBody @Valid AuthDTO.LoginRequest request) {

    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            request.membername(),
            request.password()
        )
    );
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();

    // 사용자 정보 추출
    String username = userDetails.getUsername();
    String role = userDetails.getAuthorities()
        .stream()
        .findFirst()
        .map(GrantedAuthority::getAuthority)
        .orElse("ROLE_USER"); // 기본 권한 처리

    // JWT 생성
    String token = jwtUtill.createJwt("access",username, role, jwtConfig.getAccessTokenValidityInSeconds() // 유효 시간
    );

    return ResponseEntity.ok(new LoginResponse(token));
  }


}
