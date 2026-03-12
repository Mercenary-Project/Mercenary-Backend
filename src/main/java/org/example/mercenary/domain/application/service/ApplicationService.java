package org.example.mercenary.domain.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.redisson.api.RLock; // Redisson
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final RedissonClient redissonClient;
    private final MatchRepository matchRepository;
    private final ApplicationRepository applicationRepository;

    @Transactional
    public void applyMatch(Long matchId, Long userId) {
        String lockKey = "match:" + matchId + ":lock";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 락 획득 시도 (5초 대기, 3초 점유)
            boolean available = lock.tryLock(5, 3, TimeUnit.SECONDS);

            if (!available) {
                throw new RuntimeException("현재 신청자가 너무 많아 잠시 후 다시 시도해주세요.");
            }

            // 락 획득 성공 시, DB 트랜잭션 시작
            processApplication(matchId, userId);

        } catch (InterruptedException e) {
            log.error("Redisson Lock 획득 중 인터럽트 발생: {}", e.getMessage());
            throw new RuntimeException("서버 에러가 발생했습니다.");
        } finally {
            // 락 해제 (현재 스레드가 점유하고 있을 때만)
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Transactional
    protected void processApplication(Long matchId, Long userId) {
        // A. 경기 조회 (JPA)
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 경기입니다."));

        // B. 중복 신청 검증
        if (applicationRepository.existsByMatchIdAndUserId(match, userId)) {
            throw new IllegalStateException("이미 신청한 경기입니다.");
        }

        // C. [핵심] 정원 초과 검사 (락 덕분에 안전함)
        if (match.getCurrentPlayerCount() >= match.getMaxPlayerCount()) {
            throw new IllegalStateException("마감된 경기입니다.");
        }

        // D. 신청 정보 저장 및 인원수 증가
        ApplicationEntity application = ApplicationEntity.builder()
                .match(match)
                .userId(userId)
                .build();

        applicationRepository.save(application);
        match.increasePlayerCount();
    }
}