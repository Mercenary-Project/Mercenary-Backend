package org.example.mercenary.domain.match.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
import org.example.mercenary.global.exception.BadRequestException;
import org.example.mercenary.global.exception.ForbiddenException;
import org.example.mercenary.global.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchLocationService matchLocationService;
    private final MemberRepository memberRepository;
    private final ApplicationRepository applicationRepository;
    private final Clock appClock;

    @Transactional
    public Long createMatch(MatchCreateRequestDto request, Long memberId) {
        validateMatchPlayerCount(request.getMaxPlayerCount(), request.getCurrentPlayerCount());

        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

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
        LocalDateTime now = currentDateTime();
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
                .filter(match -> !isExpired(match, now))
                .map(match -> MatchSearchResponseDto.from(match, nearbyMatchData.getOrDefault(match.getId(), 0.0)))
                .sorted((m1, m2) -> Double.compare(m1.getDistance(), m2.getDistance()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> getAllMatches() {
        LocalDateTime now = currentDateTime();
        return matchRepository.findAll().stream()
                .filter(match -> !isExpired(match, now))
                .map(match -> MatchSearchResponseDto.from(match, 0.0))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> getMyMatches(Long memberId) {
        if (memberId == null) {
            throw new BadRequestException("Authenticated member is required.");
        }

        LocalDateTime now = currentDateTime();
        return matchRepository.findAllByMemberIdOrderByMatchDateDesc(memberId).stream()
                .filter(match -> !isExpired(match, now))
                .map(match -> MatchSearchResponseDto.from(match, 0.0))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MatchDetailResponseDto getMatchDetail(Long matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found. id=" + matchId));

        if (isExpired(match, currentDateTime())) {
            throw new NotFoundException("Match not found. id=" + matchId);
        }

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
        deleteMatchResources(match);
    }

    @Transactional
    public int deleteExpiredMatches(LocalDateTime threshold) {
        List<MatchEntity> expiredMatches = matchRepository.findAllByMatchDateBefore(threshold);
        expiredMatches.forEach(this::deleteMatchResources);
        return expiredMatches.size();
    }

    private void validateMatchPlayerCount(Integer maxPlayerCount, Integer currentPlayerCount) {
        if (currentPlayerCount == null || currentPlayerCount < 1) {
            throw new BadRequestException("Current player count must be at least 1.");
        }

        if (maxPlayerCount == null) {
            throw new BadRequestException("Max player count is required.");
        }

        if (currentPlayerCount > maxPlayerCount) {
            throw new BadRequestException("Current player count cannot exceed max player count.");
        }
    }

    private MatchEntity getOwnedMatch(Long matchId, Long memberId) {
        if (memberId == null) {
            throw new BadRequestException("Authenticated member is required.");
        }

        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found. id=" + matchId));

        if (match.getMember() == null || !memberId.equals(match.getMember().getId())) {
            throw new ForbiddenException("Only the owner can modify or delete this match.");
        }

        return match;
    }

    private void deleteMatchResources(MatchEntity match) {
        applicationRepository.deleteAllByMatch(match);
        matchRepository.delete(match);
        matchLocationService.deleteMatchLocation(match.getId());
    }

    private boolean isExpired(MatchEntity match, LocalDateTime now) {
        return match.getMatchDate() != null && match.getMatchDate().isBefore(now);
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.now(appClock);
    }
}
