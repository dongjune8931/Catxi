package com.project.catxi.common.auth.kakao;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BlacklistDTO (){

    @Schema(description = "사용자 ID로 블랙리스트 등록 요청")
    public record addUserBlacklist(
        @Schema(description = "사용자 ID", example = "1")
        @NotNull(message = "사용자 ID는 필수입니다.")
        @Min(value = 1, message = "사용자 ID는 1 이상이어야 합니다.")
        Long userId,
        
        @Schema(description = "블랙리스트 기간(일). null이면 영구", example = "7")
        Integer durationDays
    ) {}

    @Schema(description = "사용자 ID로 블랙리스트 해제 요청")
    public record removeUserBlacklist(
        @Schema(description = "사용자 ID", example = "1")
        @NotNull(message = "사용자 ID는 필수입니다.")
        @Min(value = 1, message = "사용자 ID는 1 이상이어야 합니다.")
        Long userId
    ) {}

    @Schema(description = "블랙리스트 조회 응답")
    public record checkBlacklist(
        @Schema(description = "블랙리스트 등록 여부", example = "true")
        boolean isBlacklisted,
        
        @Schema(description = "사용자 ID", example = "1")
        Long userId,
        
        @Schema(description = "사용자 이메일", example = "user@example.com")
        String email
    ) {}
}