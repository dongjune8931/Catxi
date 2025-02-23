package com.example.catxi;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.catxi.oauthjwt.CustomOAuth2User;

@Controller
public class HomeController {

	@GetMapping("/")
	@ResponseBody
	public String mainAPI(){
		return "main route";
	}

}

