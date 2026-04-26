package org.example.mercenary.domain.match.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchDetailResponseDto;
import org.example.mercenary.domain.match.dto.MatchSearchRequestDto;
import org.example.mercenary.domain.match.dto.MatchSearchResponseDto;
import org.example.mercenary.domain.match.dto.MatchUpdateRequestDto;
import org.example.mercenary.domain.match.dto.PositionSlotDto;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.example.mercenary.global.exception.BadRequestException;
import org.example.mercenary.global.exception.ForbiddenException;
import org.example.mercenary.global.exception.NotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

    @CacheEvict(value = "matches", allEntries = true)
    @Transactional
    public Long createMatch(MatchCreateRequestDto request, Long memberId) {
        validateSlots(request.getSlots());

        MemberEntity member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        MatchEntity match = MatchEntity.from(request, member);

        for (PositionSlotDto slotDto : request.getSlots()) {
            match.getSlots().add(MatchPositionSlot.of(match, slotDto.getPosition(), slotDto.getRequired()));
        }

        MatchEntity savedMatch = matchRepository.save(match);

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

    @Cacheable(value = "matches", key = "'p' + #page + ':s' + #size")
    @Transactional(readOnly = true)
    public List<MatchSearchResponseDto> getAllMatches(int page, int size) {
        LocalDateTime now = currentDateTime();
        return matchRepository.findAll(PageRequest.of(page, size)).stream()
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

    @Cacheable(value = "matchDetail", key = "#matchId")
    @Transactional(readOnly = true)
    public MatchDetailResponseDto getMatchDetail(Long matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("Match not found. id=" + matchId));

        if (isExpired(match, currentDateTime())) {
            throw new NotFoundException("Match not found. id=" + matchId);
        }

        return MatchDetailResponseDto.from(match);
    }

    @Caching(evict = {
        @CacheEvict(value = "matches", allEntries = true),
        @CacheEvict(value = "matchDetail", key = "#matchId")
    })
    @Transactional
    public void updateMatch(Long matchId, MatchUpdateRequestDto request, Long memberId) {
        validateSlots(request.getSlots());

        MatchEntity match = getOwnedMatch(matchId, memberId);
        match.update(request);
        match.updateSlots(request.getSlots());
        matchLocationService.updateMatchLocation(matchId, request.getLongitude(), request.getLatitude());
    }

    @Caching(evict = {
        @CacheEvict(value = "matches", allEntries = true),
        @CacheEvict(value = "matchDetail", key = "#matchId")
    })
    @Transactional
    public void deleteMatch(Long matchId, Long memberId) {
        MatchEntity match = getOwnedMatch(matchId, memberId);
        deleteMatchResources(match);
    }

    @Caching(evict = {
        @CacheEvict(value = "matches", allEntries = true),
        @CacheEvict(value = "matchDetail", allEntries = true)
    })
    @Transactional
    public int deleteExpiredMatches(LocalDateTime threshold) {
        List<MatchEntity> expiredMatches = matchRepository.findAllByMatchDateBefore(threshold);
        expiredMatches.forEach(this::deleteMatchResources);
        return expiredMatches.size();
    }

    private void validateSlots(List<PositionSlotDto> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new BadRequestException("포지션 슬롯을 하나 이상 입력해 주세요.");
        }

        Set<Position> seen = new HashSet<>();
        for (PositionSlotDto slot : slots) {
            if (!seen.add(slot.getPosition())) {
                throw new BadRequestException("동일한 포지션을 중복으로 입력할 수 없습니다.");
            }
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
