package com.project.catxi.member.controller;

import com.project.catxi.common.api.ApiResponse;
import com.project.catxi.member.DTO.SignUpDTO;
import com.project.catxi.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MemberController {

  private final MemberService memberService;

  public MemberController(MemberService memberService) {
    this.memberService = memberService;
  }

  //@Operation(summary = "SignUp Api")
  @PostMapping("/signUp")
  public String signUp(SignUpDTO signUpDTO) {
    System.out.println(signUpDTO.getMembername());

    memberService.signUp(signUpDTO);

    return "sign up";
  }

  @GetMapping("/login")
  public String login() {
    return "login";
  }

}
