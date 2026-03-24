package org.example.mercenary.domain.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchSearchRequestDto;
import org.example.mercenary.domain.match.dto.MatchUpdateRequestDto;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.example.mercenary.global.exception.BadRequestException;
import org.example.mercenary.global.exception.ForbiddenException;
import org.example.mercenary.global.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchLocationService matchLocationService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private Clock appClock;

    @InjectMocks
    private MatchService matchService;

    @BeforeEach
    void setUpClock() {
        lenient().when(appClock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        lenient().when(appClock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("Reject create when current player count is less than one")
    void shouldRejectWhenCurrentPlayerCountLessThanOne() {
        MatchCreateRequestDto request = createRequest(10, 0);

        assertThatThrownBy(() -> matchService.createMatch(request, 1L))
                .isInstanceOf(BadRequestException.class);

        then(memberRepository).shouldHaveNoInteractions();
        then(matchRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Create match and register location when request is valid")
    void shouldCreateMatchWhenRequestValid() {
        MatchCreateRequestDto request = createRequest(10, 1);
        MemberEntity member = createMember(1L);

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(matchRepository.save(any(MatchEntity.class))).willAnswer(invocation -> {
            MatchEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 77L);
            return entity;
        });

        Long matchId = matchService.createMatch(request, 1L);

        assertThat(matchId).isEqualTo(77L);

        ArgumentCaptor<MatchEntity> entityCaptor = ArgumentCaptor.forClass(MatchEntity.class);
        then(matchRepository).should().save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getCurrentPlayerCount()).isEqualTo(1);
        assertThat(entityCaptor.getValue().getMaxPlayerCount()).isEqualTo(10);

        then(matchLocationService).should().addMatchLocation(77L, 127.0, 37.5);
    }

    @Test
    @DisplayName("Reject create when current player count exceeds max")
    void shouldRejectWhenCurrentPlayerCountExceedsMax() {
        MatchCreateRequestDto request = createRequest(5, 6);

        assertThatThrownBy(() -> matchService.createMatch(request, 1L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Return my matches ordered by match date desc")
    void shouldGetMyMatches() {
        MatchEntity newerMatch = MatchEntity.builder()
                .id(10L)
                .title("later")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2030, 1, 2, 10, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();
        MatchEntity olderMatch = MatchEntity.builder()
                .id(9L)
                .title("earlier")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2030, 1, 1, 10, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(2)
                .build();

        given(matchRepository.findAllByMemberIdOrderByMatchDateDesc(7L)).willReturn(List.of(newerMatch, olderMatch));

        var result = matchService.getMyMatches(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMatchId()).isEqualTo(10L);
        assertThat(result.get(1).getMatchId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("Hide expired matches from all matches response")
    void shouldHideExpiredMatchesFromAllMatches() {
        MatchEntity expiredMatch = MatchEntity.builder()
                .id(1L)
                .title("expired")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2025, 12, 31, 23, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();
        MatchEntity visibleMatch = MatchEntity.builder()
                .id(2L)
                .title("visible")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2026, 1, 2, 10, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();

        given(matchRepository.findAll()).willReturn(List.of(expiredMatch, visibleMatch));

        var result = matchService.getAllMatches();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Reject my matches lookup without authenticated member")
    void shouldRejectMyMatchesWithoutAuthenticatedMember() {
        assertThatThrownBy(() -> matchService.getMyMatches(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Update owned match")
    void shouldUpdateOwnedMatch() {
        MemberEntity member = createMember(1L);
        MatchEntity match = createMatch(20L, member);
        given(matchRepository.findById(20L)).willReturn(Optional.of(match));

        matchService.updateMatch(20L, createUpdateRequest(12, 4), 1L);

        assertThat(match.getTitle()).isEqualTo("updated match");
        assertThat(match.getCurrentPlayerCount()).isEqualTo(4);
        assertThat(match.getMaxPlayerCount()).isEqualTo(12);
        then(matchLocationService).should().updateMatchLocation(20L, 128.0, 36.5);
    }

    @Test
    @DisplayName("Delete owned match and linked resources")
    void shouldDeleteOwnedMatch() {
        MemberEntity member = createMember(1L);
        MatchEntity match = createMatch(30L, member);
        given(matchRepository.findById(30L)).willReturn(Optional.of(match));

        matchService.deleteMatch(30L, 1L);

        then(applicationRepository).should().deleteAllByMatch(match);
        then(matchRepository).should().delete(match);
        then(matchLocationService).should().deleteMatchLocation(30L);
    }

    @Test
    @DisplayName("Hide expired matches from nearby search response")
    void shouldHideExpiredMatchesFromNearbySearch() {
        MatchSearchRequestDto request = new MatchSearchRequestDto();
        request.setLongitude(127.0);
        request.setLatitude(37.5);
        request.setDistance(5.0);

        MatchEntity expiredMatch = MatchEntity.builder()
                .id(1L)
                .title("expired")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2025, 12, 31, 23, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();
        MatchEntity visibleMatch = MatchEntity.builder()
                .id(2L)
                .title("visible")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2026, 1, 2, 10, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();

        given(matchLocationService.findNearbyMatchIds(127.0, 37.5, 5.0)).willReturn(Map.of(1L, 1.0, 2L, 2.0));
        given(matchRepository.findAllById(anyIterable())).willReturn(List.of(expiredMatch, visibleMatch));

        var result = matchService.searchNearbyMatches(request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMatchId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Delete expired matches and linked resources")
    void shouldDeleteExpiredMatches() {
        MemberEntity owner = createMember(1L);
        MatchEntity expiredMatch = MatchEntity.builder()
                .id(41L)
                .member(owner)
                .title("expired")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2025, 1, 1, 10, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();

        LocalDateTime threshold = LocalDateTime.of(2026, 1, 1, 0, 0);
        given(matchRepository.findAllByMatchDateBefore(threshold)).willReturn(List.of(expiredMatch));

        int deletedCount = matchService.deleteExpiredMatches(threshold);

        assertThat(deletedCount).isEqualTo(1);
        then(applicationRepository).should().deleteAllByMatch(expiredMatch);
        then(matchRepository).should().delete(expiredMatch);
        then(matchLocationService).should().deleteMatchLocation(41L);
    }

    @Test
    @DisplayName("Hide expired match detail response")
    void shouldRejectExpiredMatchDetail() {
        MatchEntity expiredMatch = MatchEntity.builder()
                .id(50L)
                .title("expired")
                .content("content")
                .placeName("place")
                .district("district")
                .fullAddress("address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2025, 12, 31, 23, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();

        given(matchRepository.findById(50L)).willReturn(Optional.of(expiredMatch));

        assertThatThrownBy(() -> matchService.getMatchDetail(50L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("Reject match update when requester is not owner")
    void shouldRejectMatchUpdateWhenNotOwner() {
        MemberEntity owner = createMember(1L);
        MatchEntity match = createMatch(40L, owner);
        given(matchRepository.findById(40L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.updateMatch(40L, createUpdateRequest(12, 4), 999L))
                .isInstanceOf(ForbiddenException.class);
    }

    private MatchCreateRequestDto createRequest(int maxPlayerCount, int currentPlayerCount) {
        MatchCreateRequestDto request = new MatchCreateRequestDto();
        request.setTitle("match");
        request.setContent("content");
        request.setPlaceName("stadium");
        request.setDistrict("district");
        request.setFullAddress("address");
        request.setLatitude(37.5);
        request.setLongitude(127.0);
        request.setMatchDate(LocalDateTime.of(2030, 1, 1, 10, 0));
        request.setMaxPlayerCount(maxPlayerCount);
        request.setCurrentPlayerCount(currentPlayerCount);
        return request;
    }

    private MatchUpdateRequestDto createUpdateRequest(int maxPlayerCount, int currentPlayerCount) {
        MatchUpdateRequestDto request = new MatchUpdateRequestDto();
        request.setTitle("updated match");
        request.setContent("updated content");
        request.setPlaceName("updated place");
        request.setDistrict("updated district");
        request.setFullAddress("updated address");
        request.setLatitude(36.5);
        request.setLongitude(128.0);
        request.setMatchDate(LocalDateTime.of(2031, 1, 1, 10, 0));
        request.setMaxPlayerCount(maxPlayerCount);
        request.setCurrentPlayerCount(currentPlayerCount);
        return request;
    }

    private MemberEntity createMember(Long id) {
        MemberEntity member = MemberEntity.builder()
                .kakaoId(123L + id)
                .email("tester" + id + "@example.com")
                .nickname("tester" + id)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private MatchEntity createMatch(Long matchId, MemberEntity member) {
        return MatchEntity.builder()
                .id(matchId)
                .member(member)
                .title("old")
                .content("old content")
                .placeName("old place")
                .district("old district")
                .fullAddress("old address")
                .latitude(37.5)
                .longitude(127.0)
                .matchDate(LocalDateTime.of(2030, 1, 1, 10, 0))
                .maxPlayerCount(10)
                .currentPlayerCount(3)
                .build();
    }
}
