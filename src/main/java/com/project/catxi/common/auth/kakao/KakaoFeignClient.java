package com.project.catxi.common.auth.kakao;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "kakaoFeignClient", url = "https://kapi.kakao.com")
public interface KakaoFeignClient {

    @PostMapping(value = "/v1/user/unlink", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        KakaoUnlinkRes unlinkUser(
            @RequestHeader("Authorization") String authorization
    );
}