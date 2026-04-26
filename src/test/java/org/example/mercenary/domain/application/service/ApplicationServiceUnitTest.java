package org.example.mercenary.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.application.entity.ApplicationStatus;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;
import org.example.mercenary.domain.match.entity.MatchStatus;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.example.mercenary.global.exception.BadRequestException;
import org.example.mercenary.global.exception.ConflictException;
import org.example.mercenary.global.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceUnitTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ApplicationService applicationService;

    @BeforeEach
    void setUpTransactionTemplate() {
        // 일부 테스트는 transactionTemplate을 호출하지 않으므로 lenient로 설정
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    @DisplayName("Allow apply when applicant is not owner")
    void shouldApplyWhenApplicantIsDifferentUser() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);

        mockLock(10L);
        given(memberRepository.existsById(2L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));
        given(applicationRepository.existsByMatchAndUserId(match, 2L)).willReturn(false);

        applicationService.applyMatch(10L, 2L, Position.GK);

        then(applicationRepository).should().save(any(ApplicationEntity.class));
        MatchPositionSlot slot = match.getSlot(Position.GK);
        assertThat(slot.getFilled()).isEqualTo(0);
    }

    @Test
    @DisplayName("Reject apply when applicant is owner")
    void shouldRejectWhenApplicantIsOwner() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);

        mockLock(10L);
        given(memberRepository.existsById(1L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> applicationService.applyMatch(10L, 1L, Position.GK))
                .isInstanceOf(ConflictException.class)
                .hasMessage("본인이 만든 매치에는 참가 신청할 수 없습니다.");
    }

    @Test
    @DisplayName("Reject apply when position slot does not exist")
    void shouldRejectWhenPositionSlotNotExists() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);

        mockLock(10L);
        given(memberRepository.existsById(2L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> applicationService.applyMatch(10L, 2L, Position.ST))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("해당 포지션은 모집하지 않습니다.");
    }

    @Test
    @DisplayName("Reject apply when position slot is full")
    void shouldRejectWhenPositionSlotFull() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 1, MatchStatus.RECRUITING);

        mockLock(10L);
        given(memberRepository.existsById(2L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> applicationService.applyMatch(10L, 2L, Position.GK))
                .isInstanceOf(ConflictException.class)
                .hasMessage("해당 포지션 모집이 마감되었습니다.");
    }

    @Test
    @DisplayName("Get my application status")
    void shouldGetMyApplicationStatus() {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(2L)
                .position(Position.GK)
                .status(ApplicationStatus.READY)
                .build();
        ReflectionTestUtils.setField(application, "id", 100L);

        given(memberRepository.existsById(2L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));
        given(applicationRepository.findByMatchAndUserId(match, 2L)).willReturn(Optional.of(application));

        var result = applicationService.getMyApplicationStatus(10L, 2L);

        assertThat(result.isApplied()).isTrue();
        assertThat(result.getStatus()).isEqualTo("READY");
        assertThat(result.getApplicationId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Get applications for owner")
    void shouldGetApplicationsForOwner() {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(2L)
                .position(Position.GK)
                .status(ApplicationStatus.READY)
                .build();
        ReflectionTestUtils.setField(application, "id", 100L);

        MemberEntity applicant = MemberEntity.builder()
                .kakaoId(2000L)
                .email("applicant@example.com")
                .nickname("applicant")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(applicant, "id", 2L);

        given(matchRepository.findById(10L)).willReturn(Optional.of(match));
        given(applicationRepository.findAllByMatchOrderByCreatedAtAsc(match)).willReturn(List.of(application));
        given(memberRepository.findAllById(List.of(2L))).willReturn(List.of(applicant));

        var result = applicationService.getApplications(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApplicantNickname()).isEqualTo("applicant");
        assertThat(result.get(0).getPosition()).isEqualTo(Position.GK);
    }

    @Test
    @DisplayName("Get applied matches for applicant")
    void shouldGetAppliedMatchesForApplicant() {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(2L)
                .position(Position.GK)
                .status(ApplicationStatus.APPROVED)
                .build();
        ReflectionTestUtils.setField(application, "id", 100L);

        given(memberRepository.existsById(2L)).willReturn(true);
        given(applicationRepository.findAppliedMatchesByUserId(2L)).willReturn(List.of(application));

        var result = applicationService.getAppliedMatches(2L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getApplicationId()).isEqualTo(100L);
        assertThat(result.get(0).getMatchId()).isEqualTo(10L);
        assertThat(result.get(0).getStatus()).isEqualTo("APPROVED");
        assertThat(result.get(0).getWriterName()).isEqualTo("owner");
        assertThat(result.get(0).getPosition()).isEqualTo(Position.GK);
    }

    @Test
    @DisplayName("Return empty applied matches when nothing applied")
    void shouldReturnEmptyAppliedMatchesWhenNothingApplied() {
        given(memberRepository.existsById(2L)).willReturn(true);
        given(applicationRepository.findAppliedMatchesByUserId(2L)).willReturn(List.of());

        var result = applicationService.getAppliedMatches(2L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Cancel ready application and decrease slot filled")
    void shouldCancelReadyApplication() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 1, MatchStatus.RECRUITING);
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(2L)
                .position(Position.GK)
                .status(ApplicationStatus.READY)
                .build();

        mockLock(10L);
        given(memberRepository.existsById(2L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));
        given(applicationRepository.findByMatchAndUserId(match, 2L)).willReturn(Optional.of(application));

        applicationService.cancelApplication(10L, 2L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.CANCELED);
        assertThat(match.getSlot(Position.GK).getFilled()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reject cancel when application already processed")
    void shouldRejectCancelWhenApplicationAlreadyProcessed() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 1, MatchStatus.RECRUITING);
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(2L)
                .position(Position.GK)
                .status(ApplicationStatus.APPROVED)
                .build();

        mockLock(10L);
        given(memberRepository.existsById(2L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));
        given(applicationRepository.findByMatchAndUserId(match, 2L)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.cancelApplication(10L, 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("대기 중인 참가 신청만 취소할 수 있습니다.");
    }

    @Test
    @DisplayName("Approve application and increase slot filled")
    void shouldApproveApplication() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(2L)
                .position(Position.GK)
                .status(ApplicationStatus.READY)
                .build();

        mockLock(10L);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));
        given(applicationRepository.findByIdAndMatch(100L, match)).willReturn(Optional.of(application));

        applicationService.updateApplicationStatus(10L, 100L, 1L, ApplicationStatus.APPROVED);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(match.getSlot(Position.GK).getFilled()).isEqualTo(1);
        assertThat(match.getStatus()).isEqualTo(MatchStatus.CLOSED);
    }

    @Test
    @DisplayName("Reject application")
    void shouldRejectApplication() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(2L)
                .position(Position.GK)
                .status(ApplicationStatus.READY)
                .build();

        mockLock(10L);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));
        given(applicationRepository.findByIdAndMatch(100L, match)).willReturn(Optional.of(application));

        applicationService.updateApplicationStatus(10L, 100L, 1L, ApplicationStatus.REJECTED);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(match.getSlot(Position.GK).getFilled()).isEqualTo(0);
    }

    @Test
    @DisplayName("Reject decision when requester is not owner")
    void shouldRejectDecisionWhenRequesterIsNotOwner() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);

        mockLock(10L);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> applicationService.updateApplicationStatus(10L, 100L, 2L, ApplicationStatus.APPROVED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Reject apply when match already expired")
    void shouldRejectApplyWhenMatchAlreadyExpired() throws Exception {
        MatchEntity match = createMatchWithSlot(1L, 10L, Position.GK, 1, 0, MatchStatus.RECRUITING);
        ReflectionTestUtils.setField(match, "matchDate", LocalDateTime.now().minusMinutes(1));

        mockLock(10L);
        given(memberRepository.existsById(2L)).willReturn(true);
        given(matchRepository.findById(10L)).willReturn(Optional.of(match));

        assertThatThrownBy(() -> applicationService.applyMatch(10L, 2L, Position.GK))
                .isInstanceOf(ConflictException.class)
                .hasMessage("이미 종료된 경기에는 신청할 수 없습니다.");
    }

    private void mockLock(Long matchId) throws Exception {
        given(redissonClient.getLock("match:" + matchId + ":lock")).willReturn(lock);
        given(lock.tryLock(5, 3, TimeUnit.SECONDS)).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
    }

    private MatchEntity createMatchWithSlot(Long ownerId, Long matchId, Position position,
                                             int required, int filled, MatchStatus status) {
        MemberEntity owner = MemberEntity.builder()
                .kakaoId(1000L)
                .email("owner@example.com")
                .nickname("owner")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(owner, "id", ownerId);

        MatchEntity match = MatchEntity.builder()
                .member(owner)
                .title("match")
                .content("content")
                .placeName("stadium")
                .district("gangnam")
                .matchDate(LocalDateTime.of(2030, 1, 1, 10, 0))
                .latitude(37.5)
                .longitude(127.0)
                .fullAddress("seoul")
                .status(status)
                .build();
        ReflectionTestUtils.setField(match, "id", matchId);

        MatchPositionSlot slot = MatchPositionSlot.of(match, position, required);
        // set filled via reflection to simulate pre-existing state
        ReflectionTestUtils.setField(slot, "filled", filled);
        match.getSlots().add(slot);

        return match;
    }
}
