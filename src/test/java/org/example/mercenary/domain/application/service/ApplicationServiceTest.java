package org.example.mercenary.domain.application.service;

import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationServiceTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("Redisson 환경에서 동시 신청이 들어와도 정원만큼만 성공해야 한다")
    void concurrentApplicationTest() throws InterruptedException {
        MemberEntity member = memberRepository.save(MemberEntity.builder()
                .kakaoId(System.currentTimeMillis())
                .email("load-test@example.com")
                .nickname("load-tester")
                .role(Role.USER)
                .build());

        MatchEntity match = matchRepository.save(MatchEntity.builder()
                .member(member)
                .title("테스트 경기")
                .content("동시성 테스트")
                .placeName("강남 풋살장")
                .district("강남구")
                .matchDate(LocalDateTime.now().plusDays(1))
                .maxPlayerCount(5)
                .currentPlayerCount(0)
                .latitude(37.4979)
                .longitude(127.0276)
                .fullAddress("서울 강남구")
                .build());

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            long userId = i + 100L;
            executorService.submit(() -> {
                try {
                    applicationService.applyMatch(match.getId(), userId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        MatchEntity findMatch = matchRepository.findById(match.getId()).orElseThrow();
        assertThat(findMatch.getCurrentPlayerCount()).isEqualTo(5);
        assertThat(applicationRepository.count()).isGreaterThanOrEqualTo(4L);
    }
}
