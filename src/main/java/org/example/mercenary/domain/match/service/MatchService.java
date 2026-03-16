package org.example.mercenary.domain.match.service;

import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchDetailResponseDto;
import org.example.mercenary.domain.match.dto.MatchSearchRequestDto;
import org.example.mercenary.domain.match.dto.MatchSearchResponseDto;
import org.example.mercenary.domain.match.dto.MatchUpdateRequestDto;
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
    private final ApplicationRepository applicationRepository;

    @Transactional
    public Long createMatch(MatchCreateRequestDto request, Long memberId) {
        validateMatchPlayerCount(request.getMaxPlayerCount(), request.getCurrentPlayerCount());

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

    @Transactional
    public void updateMatch(Long matchId, MatchUpdateRequestDto request, Long memberId) {
        validateMatchPlayerCount(request.getMaxPlayerCount(), request.getCurrentPlayerCount());

        MatchEntity match = getOwnedMatch(matchId, memberId);
        match.update(request);
        matchLocationService.updateMatchLocation(matchId, request.getLongitude(), request.getLatitude());
    }

    @Transactional
    public void deleteMatch(Long matchId, Long memberId) {
        MatchEntity match = getOwnedMatch(matchId, memberId);

        applicationRepository.deleteAllByMatch(match);
        matchRepository.delete(match);
        matchLocationService.deleteMatchLocation(matchId);
    }

    private void validateMatchPlayerCount(Integer maxPlayerCount, Integer currentPlayerCount) {
        if (currentPlayerCount == null || currentPlayerCount < 1) {
            throw new IllegalArgumentException("현재 인원은 1명 이상이어야 합니다.");
        }

        if (maxPlayerCount == null) {
            throw new IllegalArgumentException("최대 인원을 입력해 주세요.");
        }

        if (currentPlayerCount > maxPlayerCount) {
            throw new IllegalArgumentException("현재 인원은 최대 인원보다 클 수 없습니다.");
        }
    }

    private MatchEntity getOwnedMatch(Long matchId, Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("인증된 사용자 정보를 찾을 수 없습니다.");
        }

        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("해당 매치를 찾을 수 없습니다. id=" + matchId));

        if (match.getMember() == null || !memberId.equals(match.getMember().getId())) {
            throw new IllegalStateException("매치 작성자만 수정과 삭제를 할 수 있습니다.");
        }

        return match;
    }
}
