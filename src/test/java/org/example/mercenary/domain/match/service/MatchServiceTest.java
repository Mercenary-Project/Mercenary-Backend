package org.example.mercenary.domain.match.service;

import org.example.mercenary.domain.match.dto.MatchCreateRequestDto;
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

import java.time.LocalDateTime;
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

    @InjectMocks
    private MatchService matchService;

    @Test
    @DisplayName("현재 인원이 1 미만이면 서비스 계층에서 거절한다")
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
        MemberEntity member = MemberEntity.builder()
                .kakaoId(123L)
                .email("tester@example.com")
                .nickname("tester")
                .role(Role.USER)
                .build();

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(matchRepository.save(any(MatchEntity.class))).willAnswer(invocation -> {
            MatchEntity entity = invocation.getArgument(0);
            return MatchEntity.builder()
                    .id(77L)
                    .member(entity.getMember())
                    .title(entity.getTitle())
                    .content(entity.getContent())
                    .placeName(entity.getPlaceName())
                    .district(entity.getDistrict())
                    .matchDate(entity.getMatchDate())
                    .maxPlayerCount(entity.getMaxPlayerCount())
                    .currentPlayerCount(entity.getCurrentPlayerCount())
                    .latitude(entity.getLatitude())
                    .longitude(entity.getLongitude())
                    .fullAddress(entity.getFullAddress())
                    .build();
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
    @DisplayName("현재 인원이 모집 인원보다 커도 매치 생성을 허용한다")
    void shouldCreateMatchWhenCurrentPlayerCountExceedsMax() {
        MatchCreateRequestDto request = createRequest(5, 6);
        MemberEntity member = MemberEntity.builder()
                .kakaoId(123L)
                .email("tester2@example.com")
                .nickname("tester2")
                .role(Role.USER)
                .build();

        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(matchRepository.save(any(MatchEntity.class))).willAnswer(invocation -> {
            MatchEntity entity = invocation.getArgument(0);
            return MatchEntity.builder()
                    .id(88L)
                    .member(entity.getMember())
                    .title(entity.getTitle())
                    .content(entity.getContent())
                    .placeName(entity.getPlaceName())
                    .district(entity.getDistrict())
                    .matchDate(entity.getMatchDate())
                    .maxPlayerCount(entity.getMaxPlayerCount())
                    .currentPlayerCount(entity.getCurrentPlayerCount())
                    .latitude(entity.getLatitude())
                    .longitude(entity.getLongitude())
                    .fullAddress(entity.getFullAddress())
                    .build();
        });

        Long matchId = matchService.createMatch(request, 1L);

        assertThat(matchId).isEqualTo(88L);
        then(matchLocationService).should().addMatchLocation(88L, 127.0, 37.5);
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
}
