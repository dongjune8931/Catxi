package com.example.catxi.oauthjwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.example.catxi.member.dto.MemberDTO;

public class CustomOAuth2User implements OAuth2User {

	private final MemberDTO memberDTO;

	public CustomOAuth2User(MemberDTO memberDTO) {
		this.memberDTO = memberDTO;
	}

	@Override
	public Map<String, Object> getAttributes() {
		// 필요 시 MemberDTO의 데이터를 매핑할 수 있습니다.
		return null;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		Collection<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(() -> memberDTO.getRole());
		return authorities;
	}

	@Override
	public String getName() {
		return memberDTO.getName();
	}

	public String getUsername() {
		return memberDTO.getUsername();
	}
}