package org.example.mercenary.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.entity.MatchStatus;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class ApplicationServiceConcurrencyTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    private MatchEntity testMatch;

    @BeforeEach
    void setUp() {
        // 매치 생성자 저장
        MemberEntity creator = memberRepository.save(
                MemberEntity.builder().kakaoId(100L).email("creator@test.com").nickname("creator").role(Role.USER)
                        .build());

        // 최대 인원 10명, 현재 1명인 매치 생성 (남은 자리 9명)
        testMatch = matchRepository.save(
                MatchEntity.builder()
                        .member(creator)
                        .title("Concurrency Test Match")
                        .content("Test Content")
                        .placeName("Test Place")
                        .district("Test District")
                        .matchDate(LocalDateTime.now().plusDays(1))
                        .maxPlayerCount(10)
                        .currentPlayerCount(1)
                        .status(MatchStatus.RECRUITING)
                        .build());
    }

    @AfterEach
    void tearDown() {
        applicationRepository.deleteAllInBatch();
        matchRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("100명의 유저가 9자리가 남은 매치에 동시 신청할 때, 9명만 성공해야 한다.")
    void applyMatch_Concurrency() throws InterruptedException {
        // given
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 신청할 가짜 유저 100명 생성
        Long[] applicantIds = new Long[threadCount];
        for (int i = 0; i < threadCount; i++) {
            MemberEntity member = memberRepository.save(
                    MemberEntity.builder()
                            .kakaoId(200L + i)
                            .email("user" + i + "@test.com")
                            .nickname("user" + i)
                            .role(Role.USER)
                            .build());
            applicantIds[i] = member.getId();
        }

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    // 동일한 매치(testMatch.getId())에 동시에 신청
                    applicationService.applyMatch(testMatch.getId(), applicantIds[index]);
                    successCount.incrementAndGet(); // 예외가 발생하지 않으면 성공
                } catch (Exception e) {
                    failCount.incrementAndGet(); // ConflictException 등의 예외 발생 시 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        MatchEntity updatedMatch = matchRepository.findById(testMatch.getId()).orElseThrow();

        // 9자리가 남았으므로 9명만 성공해야 함
        assertThat(successCount.get()).isEqualTo(9);
        // 신청 내역도 9개만 생성되어야 함
        assertThat(applicationRepository.count()).isEqualTo(9);
        // 매치의 현재 인원은 초과되지 않고 MAX 인 10명으로 유지되어야 함
        assertThat(updatedMatch.getCurrentPlayerCount()).isEqualTo(10);
        // 초과 요청(91명)은 모두 예외 응답을 받아야 함
        assertThat(failCount.get()).isEqualTo(91);
    }
}
