package org.example.mercenary.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequest(
        @Schema(description = "카카오 인가 코드", example = "SplxlOBeZQQYbYS6WxSbIA")
        @NotBlank(message = "인가 코드를 입력해 주세요.")
        String code
) {
}
