package org.example.mercenary.domain.match.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchDetailResponseDto;
import org.example.mercenary.domain.match.dto.MatchSearchRequestDto;
import org.example.mercenary.domain.match.dto.MatchSearchResponseDto;
import org.example.mercenary.domain.match.service.MatchService;
import org.example.mercenary.global.dto.ApiResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

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

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<MatchSearchResponseDto>>> getAllMatches() {
        List<MatchSearchResponseDto> results = matchService.getAllMatches();
        return ResponseEntity.ok(ApiResponseDto.success("전체 매치 조회 성공", results));
    }

    @GetMapping("/nearby")
    public ResponseEntity<?> searchNearbyMatches(@Valid @ModelAttribute MatchSearchRequestDto request,
                                                 BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            log.error("검색 요청 데이터 오류: {}", bindingResult.getAllErrors());
            return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
        }

        log.info("검색 요청 들어옴! 위도={}, 경도={}, 거리={}",
                request.getLatitude(), request.getLongitude(), request.getDistance());

        List<MatchSearchResponseDto> results = matchService.searchNearbyMatches(request);
        return ResponseEntity.ok(ApiResponseDto.success("주변 매치 검색 성공", results));
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<ApiResponseDto<MatchDetailResponseDto>> getMatchDetail(@PathVariable Long matchId) {
        MatchDetailResponseDto response = matchService.getMatchDetail(matchId);
        return ResponseEntity.ok(ApiResponseDto.success("매치 상세 조회 성공", response));
    }
}
