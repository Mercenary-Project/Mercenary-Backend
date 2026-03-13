package org.example.mercenary.domain.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DevLoginRequest(
        @NotNull Long kakaoId,
        @NotBlank String nickname
) {
}
