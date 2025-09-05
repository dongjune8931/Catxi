package com.project.catxi.common.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUnlinkRes(
        @JsonProperty("id") Long id
  ) {}