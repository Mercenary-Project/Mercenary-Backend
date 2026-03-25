package org.example.mercenary.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthTokenResponse(
        @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "회원 ID", example = "1")
        Long memberId,
        @Schema(description = "닉네임", example = "mercenary")
        String nickname
) {
}
