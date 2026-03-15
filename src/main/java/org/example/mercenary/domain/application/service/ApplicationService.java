package org.example.mercenary.domain.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mercenary.domain.application.dto.AppliedMatchResponseDto;
import org.example.mercenary.domain.application.dto.ApplicationSummaryResponseDto;
import org.example.mercenary.domain.application.dto.MyApplicationStatusResponseDto;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.application.entity.ApplicationStatus;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final RedissonClient redissonClient;
    private final MatchRepository matchRepository;
    private final ApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void applyMatch(Long matchId, Long userId) {
        validateApplicant(userId);
        executeWithMatchLock(matchId, () -> processApplication(matchId, userId));
    }

    @Transactional(readOnly = true)
    public MyApplicationStatusResponseDto getMyApplicationStatus(Long matchId, Long userId) {
        validateApplicant(userId);
        MatchEntity match = getMatch(matchId);

        return applicationRepository.findByMatchAndUserId(match, userId)
                .map(MyApplicationStatusResponseDto::from)
                .orElseGet(MyApplicationStatusResponseDto::notApplied);
    }

    @Transactional(readOnly = true)
    public List<AppliedMatchResponseDto> getAppliedMatches(Long userId) {
        validateApplicant(userId);

        return applicationRepository.findAppliedMatchesByUserId(userId).stream()
                .map(AppliedMatchResponseDto::from)
                .toList();
    }

    @Transactional
    public void cancelApplication(Long matchId, Long userId) {
        validateApplicant(userId);
        executeWithMatchLock(matchId, () -> processApplicationCancellation(matchId, userId));
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummaryResponseDto> getApplications(Long matchId, Long memberId) {
        MatchEntity match = getOwnedMatch(matchId, memberId);
        List<ApplicationEntity> applications = applicationRepository.findAllByMatchOrderByCreatedAtAsc(match);
        Map<Long, MemberEntity> memberMap = memberRepository.findAllById(
                        applications.stream().map(ApplicationEntity::getUserId).distinct().toList()
                ).stream()
                .collect(Collectors.toMap(MemberEntity::getId, member -> member));

        return applications.stream()
                .map(application -> ApplicationSummaryResponseDto.from(
                        application,
                        memberMap.get(application.getUserId())
                ))
                .toList();
    }

    @Transactional
    public void updateApplicationStatus(Long matchId, Long applicationId, Long memberId, ApplicationStatus status) {
        if (status != ApplicationStatus.APPROVED && status != ApplicationStatus.REJECTED) {
            throw new IllegalArgumentException("신청 상태는 APPROVED 또는 REJECTED 만 처리할 수 있습니다.");
        }

        executeWithMatchLock(matchId, () -> processApplicationDecision(matchId, applicationId, memberId, status));
    }

    protected void processApplication(Long matchId, Long userId) {
        MatchEntity match = getMatch(matchId);

        if (match.getMember() != null && Objects.equals(match.getMember().getId(), userId)) {
            throw new IllegalStateException("본인이 만든 매치에는 참가 신청할 수 없습니다.");
        }

        if (applicationRepository.existsByMatchAndUserId(match, userId)) {
            throw new IllegalStateException("이미 참가 신청한 매치입니다.");
        }

        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(userId)
                .build();

        applicationRepository.save(application);
    }

    protected void processApplicationDecision(Long matchId, Long applicationId, Long memberId, ApplicationStatus status) {
        MatchEntity match = getOwnedMatch(matchId, memberId);
        ApplicationEntity application = applicationRepository.findByIdAndMatch(applicationId, match)
                .orElseThrow(() -> new IllegalArgumentException("해당 신청 내역을 찾을 수 없습니다."));

        if (application.getStatus() != ApplicationStatus.READY) {
            throw new IllegalStateException("대기 중인 신청만 처리할 수 있습니다.");
        }

        if (status == ApplicationStatus.APPROVED) {
            if (match.getCurrentPlayerCount() >= match.getMaxPlayerCount()) {
                throw new IllegalStateException("모집이 마감된 매치입니다.");
            }
            application.approve();
            match.increasePlayerCount();
            return;
        }

        application.reject();
    }

    protected void processApplicationCancellation(Long matchId, Long userId) {
        MatchEntity match = getMatch(matchId);
        ApplicationEntity application = applicationRepository.findByMatchAndUserId(match, userId)
                .orElseThrow(() -> new IllegalArgumentException("취소할 참가 신청을 찾을 수 없습니다."));

        if (application.getStatus() != ApplicationStatus.READY) {
            throw new IllegalStateException("대기 중인 참가 신청만 취소할 수 있습니다.");
        }

        application.cancel();
    }

    private void executeWithMatchLock(Long matchId, Runnable action) {
        String lockKey = "match:" + matchId + ":lock";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean available = lock.tryLock(5, 3, TimeUnit.SECONDS);

            if (!available) {
                throw new IllegalStateException("요청이 많아 잠시 후 다시 시도해 주세요.");
            }

            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to acquire application lock", e);
            throw new IllegalStateException("신청 처리 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void validateApplicant(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("인증된 사용자 정보가 없습니다.");
        }

        if (!memberRepository.existsById(userId)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
    }

    private MatchEntity getMatch(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 매치입니다."));
    }

    private MatchEntity getOwnedMatch(Long matchId, Long memberId) {
        MatchEntity match = getMatch(matchId);
        if (match.getMember() == null || !Objects.equals(match.getMember().getId(), memberId)) {
            throw new IllegalStateException("해당 매치의 작성자만 접근할 수 있습니다.");
        }
        return match;
    }
}
