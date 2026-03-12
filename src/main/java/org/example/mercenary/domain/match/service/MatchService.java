package org.example.mercenary.domain.match.service;

import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchDetailResponseDto;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.match.dto.MatchSearchRequestDto;
import org.example.mercenary.domain.match.dto.MatchSearchResponseDto;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchService {
    private final MatchRepository matchRepository;
    private final MatchLocationService matchLocationService;
    private final MemberRepository memberRepository;

    /**
     * 1. 매치 생성 (DB 저장 + Redis Geo 동기화)
     */
    @Transactional
    public Long createMatch(MatchCreateRequestDto request, Long memberId) {

        //  작성자(Member) 찾기
        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("회원을 찾을 수 없습니다."));

        //  Entity 생성
        MatchEntity newMatch = MatchEntity.from(request, member);

        //  MySQL에 저장
        MatchEntity savedMatch = matchRepository.save(newMatch);

        //  Redis Geo에 위치 정보 동기화
        matchLocationService.addMatchLocation(
                savedMatch.getId(),
                request.getLongitude(),
                request.getLatitude()
        );

        return savedMatch.getId();
    }

    /**
     * 2. 내 주변 매치 검색 (Redis Geo + MySQL 조합)
     */
    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> searchNearbyMatches(MatchSearchRequestDto request) {

        // Redis Geo로 주변 ID 조회
        Map<Long, Double> nearbyMatchData = matchLocationService.findNearbyMatchIds(
                request.getLongitude(),
                request.getLatitude(),
                request.getDistance()
        );

        if (nearbyMatchData.isEmpty()) {
            return List.of();
        }

        // MySQL 조회
        List<Long> matchIds = nearbyMatchData.keySet().stream().toList();
        List<MatchEntity> matches = matchRepository.findAllById(matchIds);

        // 거리 정보 포함하여 DTO 변환
        return matches.stream()
                .map(match -> {
                    Double distance = nearbyMatchData.getOrDefault(match.getId(), 0.0);
                    return MatchSearchResponseDto.from(match, distance);
                })
                .sorted((m1, m2) -> Double.compare(m1.getDistance(), m2.getDistance()))
                .collect(Collectors.toList());
    }

    /**
     * 3. 전체 매치 조회 (단순 목록)
     *  Controller의 getAllMatches() 요청을 처리하기 위해 필요합니다.
     */
    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> getAllMatches() {
        // 모든 매치를 DB에서 가져옵니다. (데이터가 많아지면 페이징 필요, 지금은 전체 조회)
        List<MatchEntity> matches = matchRepository.findAll();

        return matches.stream()
                .map(match -> MatchSearchResponseDto.from(match, 0.0)) // 거리 정보는 없으므로 0.0으로 설정
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MatchDetailResponseDto getMatchDetail(Long matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("해당 매치를 찾을 수 없습니다. id=" + matchId));

        return MatchDetailResponseDto.from(match);
    }
}