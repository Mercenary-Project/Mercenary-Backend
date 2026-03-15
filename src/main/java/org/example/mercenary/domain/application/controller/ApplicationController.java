package org.example.mercenary.domain.application.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.application.dto.AppliedMatchResponseDto;
import org.example.mercenary.domain.application.dto.ApplicationDecisionRequestDto;
import org.example.mercenary.domain.application.dto.ApplicationSummaryResponseDto;
import org.example.mercenary.domain.application.dto.MyApplicationStatusResponseDto;
import org.example.mercenary.domain.application.service.ApplicationService;
import org.example.mercenary.global.dto.ApiResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
        return ResponseEntity.ok(ApiResponseDto.success("참가 신청이 성공적으로 완료되었습니다.", null));
    }

    @GetMapping("/{matchId}/application/me")
    public ResponseEntity<ApiResponseDto<MyApplicationStatusResponseDto>> getMyApplicationStatus(
            @PathVariable Long matchId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                "내 참가 신청 상태 조회 성공",
                applicationService.getMyApplicationStatus(matchId, memberId)
        ));
    }

    @DeleteMapping("/{matchId}/application/me")
    public ResponseEntity<ApiResponseDto<String>> cancelMyApplication(
            @PathVariable Long matchId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        applicationService.cancelApplication(matchId, memberId);
        return ResponseEntity.ok(ApiResponseDto.success("참가 신청이 취소되었습니다.", null));
    }

    @GetMapping("/applied")
    public ResponseEntity<ApiResponseDto<List<AppliedMatchResponseDto>>> getAppliedMatches(
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                "내가 신청한 매치 목록 조회 성공",
                applicationService.getAppliedMatches(memberId)
        ));
    }

    @GetMapping("/{matchId}/applications")
    public ResponseEntity<ApiResponseDto<List<ApplicationSummaryResponseDto>>> getApplications(
            @PathVariable Long matchId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                "참가 신청 목록 조회 성공",
                applicationService.getApplications(matchId, memberId)
        ));
    }

    @PatchMapping("/{matchId}/applications/{applicationId}")
    public ResponseEntity<ApiResponseDto<String>> updateApplicationStatus(
            @PathVariable Long matchId,
            @PathVariable Long applicationId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody ApplicationDecisionRequestDto request
    ) {
        applicationService.updateApplicationStatus(matchId, applicationId, memberId, request.getStatus());
        return ResponseEntity.ok(ApiResponseDto.success("참가 신청 상태가 변경되었습니다.", null));
    }
}
