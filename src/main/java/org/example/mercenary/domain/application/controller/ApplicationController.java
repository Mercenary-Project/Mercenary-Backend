package org.example.mercenary.domain.application.controller;

import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.application.service.ApplicationService;
import org.example.mercenary.global.dto.ApiResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping("/{matchId}/apply")
    public ResponseEntity<ApiResponseDto<String>> applyMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        applicationService.applyMatch(matchId, memberId);
        return ResponseEntity.ok(ApiResponseDto.success("용병 신청이 성공적으로 완료되었습니다.", null));
    }
}
