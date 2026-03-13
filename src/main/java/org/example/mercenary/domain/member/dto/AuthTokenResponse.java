package org.example.mercenary.domain.member.dto;

public record AuthTokenResponse(
        String accessToken,
        Long memberId,
        String nickname
) {
}
