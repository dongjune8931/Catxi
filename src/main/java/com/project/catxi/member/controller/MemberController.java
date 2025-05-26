package com.project.catxi.member.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.member.DTO.IdResponse;
import com.project.catxi.member.DTO.SignUpDTO;
import com.project.catxi.member.service.MemberService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemberController {

  private final MemberService memberService;

  public MemberController(MemberService memberService) {
    this.memberService = memberService;
  }

  //@Operation(summary = "SignUp Api")
  @PostMapping("/signUp")
  public ResponseEntity<IdResponse> signUp(@RequestBody @Valid SignUpDTO dto) {
    Long id = memberService.signUp(dto);
    return ResponseEntity
        .created(URI.create("/api/members/" + id))
        .body(new IdResponse(id));
  }


  @GetMapping("/login")
  public String login() {
    return "login";
  }

}
