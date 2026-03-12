package org.example.mercenary.domain.application.service;

import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

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
    @Disabled("Redisson/MySQL integration timing depends on external local state; stabilize in a dedicated integration test setup.")
    @DisplayName("Redisson 환경에서 동시 신청이 들어와도 정원만큼만 성공해야 한다")
    void concurrentApplicationTest() throws InterruptedException {
        long seed = System.currentTimeMillis();

        MemberEntity owner = memberRepository.save(MemberEntity.builder()
                .kakaoId(seed)
                .email("load-test-" + seed + "@example.com")
                .nickname("load-tester")
                .role(Role.USER)
                .build());

        MatchEntity match = matchRepository.save(MatchEntity.builder()
                .member(owner)
                .title("테스트 경기")
                .content("동시성 테스트")
                .placeName("강남 구장")
                .district("강남구")
                .matchDate(LocalDateTime.now().plusDays(1))
                .maxPlayerCount(5)
                .currentPlayerCount(1)
                .latitude(37.4979)
                .longitude(127.0276)
                .fullAddress("서울 강남구")
                .build());

        List<Long> applicantIds = IntStream.range(0, 100)
                .mapToObj(i -> memberRepository.save(MemberEntity.builder()
                        .kakaoId(seed + i + 1L)
                        .email("applicant-" + seed + "-" + i + "@example.com")
                        .nickname("applicant-" + i)
                        .role(Role.USER)
                        .build()).getId())
                .toList();

        int threadCount = applicantIds.size();
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (Long applicantId : applicantIds) {
            executorService.submit(() -> {
                try {
                    applicationService.applyMatch(match.getId(), applicantId);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        MatchEntity findMatch = matchRepository.findById(match.getId()).orElseThrow();
        assertThat(findMatch.getCurrentPlayerCount()).isEqualTo(5);
        assertThat(applicationRepository.count()).isEqualTo(4L);
    }
}
