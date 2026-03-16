package org.example.mercenary.domain.match.service;

import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
import org.example.mercenary.domain.match.dto.MatchUpdateRequestDto;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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

    @InjectMocks
    private MatchService matchService;

    @Test
    @DisplayName("현재 인원이 1 미만이면 예외가 발생한다")
    void shouldRejectWhenCurrentPlayerCountLessThanOne() {
        MatchCreateRequestDto request = createRequest(10, 0);

        assertThatThrownBy(() -> matchService.createMatch(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 인원은 1명 이상이어야 합니다.");

        then(memberRepository).shouldHaveNoInteractions();
        then(matchRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("유효한 요청이면 매치를 저장하고 위치 정보를 등록한다")
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
    @DisplayName("현재 인원이 최대 인원보다 크면 예외가 발생한다")
    void shouldRejectWhenCurrentPlayerCountExceedsMax() {
        MatchCreateRequestDto request = createRequest(5, 6);

        assertThatThrownBy(() -> matchService.createMatch(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("현재 인원은 최대 인원보다 클 수 없습니다.");
    }

    @Test
    @DisplayName("로그인 사용자는 내가 작성한 매치 목록을 조회할 수 있다")
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

        given(matchRepository.findAllByMemberIdOrderByMatchDateDesc(7L))
                .willReturn(List.of(newerMatch, olderMatch));

        var result = matchService.getMyMatches(7L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMatchId()).isEqualTo(10L);
        assertThat(result.get(0).getTitle()).isEqualTo("later");
        assertThat(result.get(1).getMatchId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("인증 정보가 없으면 내 매치 목록을 조회할 수 없다")
    void shouldRejectMyMatchesWithoutAuthenticatedMember() {
        assertThatThrownBy(() -> matchService.getMyMatches(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("인증된 사용자 정보를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("작성자는 매치를 수정할 수 있다")
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
    @DisplayName("작성자는 매치를 삭제할 수 있다")
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
    @DisplayName("작성자가 아니면 매치를 수정할 수 없다")
    void shouldRejectMatchUpdateWhenNotOwner() {
        MemberEntity owner = createMember(1L);
        MatchEntity match = createMatch(40L, owner);
        given(matchRepository.findById(40L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.updateMatch(40L, createUpdateRequest(12, 4), 999L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("매치 작성자만 수정과 삭제를 할 수 있습니다.");
    }

    private MatchCreateRequestDto createRequest(int maxPlayerCount, int currentPlayerCount) {
        MatchCreateRequestDto request = new MatchCreateRequestDto();
        request.setTitle("매치");
        request.setContent("내용");
        request.setPlaceName("구장");
        request.setDistrict("강남구");
        request.setFullAddress("서울 강남구");
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
