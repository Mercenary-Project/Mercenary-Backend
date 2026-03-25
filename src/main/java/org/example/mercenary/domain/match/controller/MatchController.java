package org.example.mercenary.domain.match.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchDetailResponseDto;
import org.example.mercenary.domain.match.dto.MatchSearchRequestDto;
import org.example.mercenary.domain.match.dto.MatchSearchResponseDto;
import org.example.mercenary.domain.match.dto.MatchUpdateRequestDto;
import org.example.mercenary.domain.match.service.MatchService;
import org.example.mercenary.global.dto.ApiResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Tag(name = "Match", description = "경기 게시글 API")
public class MatchController {

    private final MatchService matchService;

    @Operation(summary = "매치 생성", description = "새 경기 게시글을 생성합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 오류"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "작성자 없음")
    })
    @PostMapping
    public ResponseEntity<ApiResponseDto<Long>> createMatch(
            @Valid @RequestBody MatchCreateRequestDto request,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        log.info("매치 생성 요청 - 작성자 ID: {}, 제목: {}", memberId, request.getTitle());
        Long matchId = matchService.createMatch(request, memberId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDto.success("매치가 성공적으로 생성되었습니다.", matchId));
    }

    @Operation(summary = "전체 매치 조회", description = "현재 조회 가능한 경기 게시글 목록을 반환합니다. 지난 경기는 제외됩니다.")
    @GetMapping
    public ResponseEntity<ApiResponseDto<List<MatchSearchResponseDto>>> getAllMatches() {
        List<MatchSearchResponseDto> results = matchService.getAllMatches();
        return ResponseEntity.ok(ApiResponseDto.success("전체 매치 조회 성공", results));
    }

    @Operation(summary = "내 매치 조회", description = "현재 로그인한 사용자가 작성한 경기 게시글 목록을 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/my")
    public ResponseEntity<ApiResponseDto<List<MatchSearchResponseDto>>> getMyMatches(
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        List<MatchSearchResponseDto> results = matchService.getMyMatches(memberId);
        return ResponseEntity.ok(ApiResponseDto.success("내가 작성한 매치 조회 성공", results));
    }

    @Operation(summary = "주변 매치 조회", description = "위도, 경도, 거리 기준으로 주변 경기 게시글을 조회합니다. 지난 경기는 제외됩니다.")
    @GetMapping("/nearby")
    public ResponseEntity<?> searchNearbyMatches(@Valid @ModelAttribute MatchSearchRequestDto request,
                                                 BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            log.error("검색 요청 데이터 오류: {}", bindingResult.getAllErrors());
            return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
        }

        log.info("검색 요청 들어옴: 위도={}, 경도={}, 거리={}",
                request.getLatitude(), request.getLongitude(), request.getDistance());

        List<MatchSearchResponseDto> results = matchService.searchNearbyMatches(request);
        return ResponseEntity.ok(ApiResponseDto.success("주변 매치 검색 성공", results));
    }

    @Operation(summary = "매치 상세 조회", description = "특정 경기 게시글의 상세 정보를 조회합니다. 지난 경기는 조회되지 않습니다.")
    @GetMapping("/{matchId}")
    public ResponseEntity<ApiResponseDto<MatchDetailResponseDto>> getMatchDetail(@PathVariable Long matchId) {
        MatchDetailResponseDto response = matchService.getMatchDetail(matchId);
        return ResponseEntity.ok(ApiResponseDto.success("매치 상세 조회 성공", response));
    }

    @Operation(summary = "매치 수정", description = "작성자만 경기 게시글을 수정할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{matchId}")
    public ResponseEntity<ApiResponseDto<String>> updateMatch(
            @PathVariable Long matchId,
            @Valid @RequestBody MatchUpdateRequestDto request,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        matchService.updateMatch(matchId, request, memberId);
        return ResponseEntity.ok(ApiResponseDto.success("매치가 성공적으로 수정되었습니다.", null));
    }

    @Operation(summary = "매치 삭제", description = "작성자만 경기 게시글을 삭제할 수 있습니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{matchId}")
    public ResponseEntity<ApiResponseDto<String>> deleteMatch(
            @PathVariable Long matchId,
            @AuthenticationPrincipal(expression = "memberId") Long memberId
    ) {
        matchService.deleteMatch(matchId, memberId);
        return ResponseEntity.ok(ApiResponseDto.success("매치가 성공적으로 삭제되었습니다.", null));
    }
}
