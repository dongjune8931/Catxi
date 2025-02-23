package com.example.catxi.oauthjwt;

import java.util.Map;


@SuppressWarnings("unchecked")
public class KakaoResponse implements OAuth2Response {

	private final Map<String, Object> attributes;

	public KakaoResponse(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	@Override
	public String getProvider() {
		return "kakao";
	}

	@Override
	public String getProviderId() {
		// 카카오의 고유 id는 최상위 속성 "id"에 있음
		return attributes.get("id").toString();
	}

	@Override
	public String getEmail() {
		return "";
	}

	@Override
	public String getName() {
		// 닉네임은 "properties" 내 "nickname"에 있음
		Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
		return properties.get("nickname").toString();
	}
}
