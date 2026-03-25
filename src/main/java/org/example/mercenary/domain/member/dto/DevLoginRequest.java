package org.example.mercenary.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DevLoginRequest(
        @Schema(description = "테스트용 카카오 ID", example = "1001")
        @NotNull Long kakaoId,
        @Schema(description = "테스트용 닉네임", example = "test-user-1")
        @NotBlank String nickname
) {
}
