package com.example.catxi.oauthjwt;

import java.time.LocalDateTime;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.catxi.member.dto.MemberDTO;
import com.example.catxi.member.entity.Member;
import com.example.catxi.member.repository.MemberRepository;


@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

	private final MemberRepository memberRepository;

	public CustomOAuth2UserService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		OAuth2User oAuth2User = super.loadUser(userRequest);

		// 카카오 응답을 KakaoResponse로 변환
		OAuth2Response oAuth2Response = new KakaoResponse(oAuth2User.getAttributes());


		// 고유 username은 "kakao_아이디" 형태로 생성
		String username = oAuth2Response.getProvider() + "_" + oAuth2Response.getProviderId();
		Member member = memberRepository.findByUsername(username);

		if(member == null) {
			// 새로운 회원 생성
			member = Member.builder()
				.username(username)
				.email("")
				.name(oAuth2Response.getName())
				.role("ROLE_USER")
				.createdAt(LocalDateTime.now())
				.build();
			memberRepository.save(member);
		} else {
			// 기존 회원 정보 업데이트 (필요 시)
			member.setName(oAuth2Response.getName());
			memberRepository.save(member);
		}

		MemberDTO memberDTO = new MemberDTO();
		memberDTO.setUsername(member.getUsername());
		memberDTO.setName(member.getName());
		memberDTO.setRole(member.getRole());

		return new CustomOAuth2User(memberDTO);
	}
}