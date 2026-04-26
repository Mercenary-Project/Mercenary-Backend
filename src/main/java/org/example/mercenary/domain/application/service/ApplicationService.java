package org.example.mercenary.domain.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mercenary.domain.application.dto.AppliedMatchResponseDto;
import org.example.mercenary.domain.application.dto.ApplicationSummaryResponseDto;
import org.example.mercenary.domain.application.dto.MyApplicationStatusResponseDto;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.application.entity.ApplicationStatus;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.example.mercenary.global.exception.BadRequestException;
import org.example.mercenary.global.exception.ConflictException;
import org.example.mercenary.global.exception.ForbiddenException;
import org.example.mercenary.global.exception.NotFoundException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final RedissonClient redissonClient;
    private final MatchRepository matchRepository;
    private final ApplicationRepository applicationRepository;
    private final MemberRepository memberRepository;
    private final TransactionTemplate transactionTemplate;

    @CacheEvict(value = "matchDetail", key = "#matchId")
    public void applyMatch(Long matchId, Long userId, Position position) {
        validateApplicant(userId);
        executeWithMatchLock(matchId,
                () -> transactionTemplate.executeWithoutResult(status -> processApplication(matchId, userId, position)));
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

    @CacheEvict(value = "matchDetail", key = "#matchId")
    public void cancelApplication(Long matchId, Long userId) {
        validateApplicant(userId);
        executeWithMatchLock(matchId, () -> transactionTemplate
                .executeWithoutResult(status -> processApplicationCancellation(matchId, userId)));
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummaryResponseDto> getApplications(Long matchId, Long memberId) {
        MatchEntity match = getOwnedMatch(matchId, memberId);
        List<ApplicationEntity> applications = applicationRepository.findAllByMatchOrderByCreatedAtAsc(match);
        Map<Long, MemberEntity> memberMap = memberRepository.findAllById(
                applications.stream().map(ApplicationEntity::getUserId).distinct().toList()).stream()
                .collect(Collectors.toMap(MemberEntity::getId, member -> member));

        return applications.stream()
                .map(application -> ApplicationSummaryResponseDto.from(application,
                        memberMap.get(application.getUserId())))
                .toList();
    }

    @CacheEvict(value = "matchDetail", key = "#matchId")
    public void updateApplicationStatus(Long matchId, Long applicationId, Long memberId, ApplicationStatus status) {
        if (status != ApplicationStatus.APPROVED && status != ApplicationStatus.REJECTED) {
            throw new BadRequestException("신청 상태는 APPROVED 또는 REJECTED만 처리할 수 있습니다.");
        }

        executeWithMatchLock(matchId, () -> transactionTemplate
                .executeWithoutResult(s -> processApplicationDecision(matchId, applicationId, memberId, status)));
    }

    protected void processApplication(Long matchId, Long userId, Position position) {
        MatchEntity match = getMatch(matchId);

        if (match.getMatchDate() != null && match.getMatchDate().isBefore(LocalDateTime.now())) {
            throw new ConflictException("이미 종료된 경기에는 신청할 수 없습니다.");
        }

        MatchPositionSlot slot = match.getSlot(position);
        if (slot == null) {
            throw new BadRequestException("해당 포지션은 모집하지 않습니다.");
        }

        if (!slot.isAvailable()) {
            throw new ConflictException("해당 포지션 모집이 마감되었습니다.");
        }

        if (match.getMember() != null && Objects.equals(match.getMember().getId(), userId)) {
            throw new ConflictException("본인이 만든 매치에는 참가 신청할 수 없습니다.");
        }

        if (applicationRepository.existsByMatchAndUserId(match, userId)) {
            throw new ConflictException("이미 참가 신청한 매치입니다.");
        }

        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(userId)
                .position(position)
                .build();

        applicationRepository.save(application);

    }

    protected void processApplicationDecision(Long matchId, Long applicationId, Long memberId,
            ApplicationStatus status) {
        MatchEntity match = getOwnedMatch(matchId, memberId);
        ApplicationEntity application = applicationRepository.findByIdAndMatch(applicationId, match)
                .orElseThrow(() -> new NotFoundException("해당 신청 내역을 찾을 수 없습니다."));

        if (application.getStatus() != ApplicationStatus.READY) {
            throw new ConflictException("대기 중인 신청만 처리할 수 있습니다.");
        }

        if (status == ApplicationStatus.APPROVED) {
            MatchPositionSlot slot = match.getSlot(application.getPosition());
            if (slot == null || !slot.isAvailable()) {
                throw new ConflictException("해당 포지션 모집이 마감된 매치입니다.");
            }
            application.approve();
            slot.increaseFilled();
            if (match.isFullyBooked()) {
                match.close();
            }
            return;
        }

        application.reject();
    }

    protected void processApplicationCancellation(Long matchId, Long userId) {
        MatchEntity match = getMatch(matchId);
        ApplicationEntity application = applicationRepository.findByMatchAndUserId(match, userId)
                .orElseThrow(() -> new NotFoundException("취소할 참가 신청을 찾을 수 없습니다."));

        if (application.getStatus() != ApplicationStatus.READY) {
            throw new ConflictException("대기 중인 참가 신청만 취소할 수 있습니다.");
        }

        application.cancel();

    }

    private void executeWithMatchLock(Long matchId, Runnable action) {
        String lockKey = "match:" + matchId + ":lock";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean available = lock.tryLock(5, 3, TimeUnit.SECONDS);

            if (!available) {
                throw new ConflictException("요청이 많아 잠시 후 다시 시도해 주세요.");
            }

            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to acquire application lock", e);
            throw new ConflictException("신청 처리 중 오류가 발생했습니다.");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void validateApplicant(Long userId) {
        if (userId == null) {
            throw new BadRequestException("인증된 사용자 정보가 없습니다.");
        }

        if (!memberRepository.existsById(userId)) {
            throw new NotFoundException("사용자를 찾을 수 없습니다.");
        }
    }

    private MatchEntity getMatch(Long matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 매치입니다."));
    }

    private MatchEntity getOwnedMatch(Long matchId, Long memberId) {
        MatchEntity match = getMatch(matchId);
        if (match.getMember() == null || !Objects.equals(match.getMember().getId(), memberId)) {
            throw new ForbiddenException("해당 매치의 작성자만 접근할 수 있습니다.");
        }
        return match;
    }
}
