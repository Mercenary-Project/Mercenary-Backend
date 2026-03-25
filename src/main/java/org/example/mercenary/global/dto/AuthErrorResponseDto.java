package org.example.mercenary.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 실패 응답")
public record AuthErrorResponseDto(
        @Schema(description = "인증 오류 코드", example = "TOKEN_EXPIRED")
        String code,
        @Schema(description = "오류 메시지", example = "만료된 토큰입니다.")
        String message
) {
}
