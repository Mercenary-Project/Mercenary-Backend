package org.example.mercenary.domain.application.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.application.dto.AppliedMatchResponseDto;
import org.example.mercenary.domain.application.dto.ApplicationDecisionRequestDto;
import org.example.mercenary.domain.application.dto.ApplicationRequestDto;
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

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Tag(name = "Application", description = "경기 신청 API")
public class ApplicationController {

    private final ApplicationService applicationService;

    @Operation(summary = "매치 신청", description = "특정 경기 게시글에 참가 신청합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "신청 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 포지션 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "매치 없음"),
            @ApiResponse(responseCode = "409", description = "중복 신청, 마감, 종료 경기")
    })
    @PostMapping("/{matchId}/apply")
    public ResponseEntity<ApiResponseDto<String>> applyMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId,
            @Valid @RequestBody ApplicationRequestDto request
    ) {
        applicationService.applyMatch(matchId, memberId, request.getPosition());
        return ResponseEntity.ok(ApiResponseDto.success("참가 신청이 성공적으로 완료되었습니다.", null));
    }

    @Operation(summary = "내 신청 상태 조회", description = "특정 매치에 대한 내 신청 여부와 상태를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
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

    @Operation(summary = "내 신청 취소", description = "대기 상태의 내 신청을 취소합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{matchId}/application/me")
    public ResponseEntity<ApiResponseDto<String>> cancelMyApplication(
            @PathVariable Long matchId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        applicationService.cancelApplication(matchId, memberId);
        return ResponseEntity.ok(ApiResponseDto.success("참가 신청을 취소했습니다.", null));
    }

    @Operation(summary = "내 신청 목록 조회", description = "현재 로그인한 사용자가 신청한 경기 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/applied")
    public ResponseEntity<ApiResponseDto<List<AppliedMatchResponseDto>>> getAppliedMatches(
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        return ResponseEntity.ok(ApiResponseDto.success(
                "내가 신청한 매치 목록 조회 성공",
                applicationService.getAppliedMatches(memberId)
        ));
    }

    @Operation(summary = "신청 목록 조회", description = "매치 작성자가 특정 경기의 신청 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
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

    @Operation(summary = "신청 승인 또는 거절", description = "매치 작성자가 신청 상태를 APPROVED 또는 REJECTED로 변경합니다.")
    @SecurityRequirement(name = "bearerAuth")
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
