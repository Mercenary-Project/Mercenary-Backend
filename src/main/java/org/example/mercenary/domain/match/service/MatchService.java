package org.example.mercenary.domain.match.service;

import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchDetailResponseDto;
import org.example.mercenary.domain.match.dto.MatchSearchRequestDto;
import org.example.mercenary.domain.match.dto.MatchSearchResponseDto;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
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

    @Transactional
    public Long createMatch(MatchCreateRequestDto request, Long memberId) {
        validateCreateMatchRequest(request);

        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));

        MatchEntity savedMatch = matchRepository.save(MatchEntity.from(request, member));

        matchLocationService.addMatchLocation(
                savedMatch.getId(),
                request.getLongitude(),
                request.getLatitude()
        );

        return savedMatch.getId();
    }

    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> searchNearbyMatches(MatchSearchRequestDto request) {
        Map<Long, Double> nearbyMatchData = matchLocationService.findNearbyMatchIds(
                request.getLongitude(),
                request.getLatitude(),
                request.getDistance()
        );

        if (nearbyMatchData.isEmpty()) {
            return List.of();
        }

        List<Long> matchIds = nearbyMatchData.keySet().stream().toList();
        List<MatchEntity> matches = matchRepository.findAllById(matchIds);

        return matches.stream()
                .map(match -> {
                    Double distance = nearbyMatchData.getOrDefault(match.getId(), 0.0);
                    return MatchSearchResponseDto.from(match, distance);
                })
                .sorted((m1, m2) -> Double.compare(m1.getDistance(), m2.getDistance()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> getAllMatches() {
        return matchRepository.findAll().stream()
                .map(match -> MatchSearchResponseDto.from(match, 0.0))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> getMyMatches(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        return matchRepository.findAllByMemberIdOrderByMatchDateDesc(memberId).stream()
                .map(match -> MatchSearchResponseDto.from(match, 0.0))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MatchDetailResponseDto getMatchDetail(Long matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("해당 매치를 찾을 수 없습니다. id=" + matchId));

        return MatchDetailResponseDto.from(match);
    }

    private void validateCreateMatchRequest(MatchCreateRequestDto request) {
        if (request.getCurrentPlayerCount() == null || request.getCurrentPlayerCount() < 1) {
            throw new IllegalArgumentException("현재 인원은 1명 이상이어야 합니다.");
        }

        if (request.getMaxPlayerCount() == null) {
            throw new IllegalArgumentException("최대 인원을 입력해 주세요.");
        }

    }
}
