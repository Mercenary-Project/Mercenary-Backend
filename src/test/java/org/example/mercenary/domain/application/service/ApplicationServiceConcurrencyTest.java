package org.example.mercenary.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;
import org.example.mercenary.domain.match.entity.MatchStatus;
import org.example.mercenary.domain.match.repository.MatchPositionSlotRepository;
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

    @Autowired
    private MatchPositionSlotRepository matchPositionSlotRepository;

    private MatchEntity testMatch;

    @BeforeEach
    void setUp() {
        MemberEntity creator = memberRepository.save(
                MemberEntity.builder().kakaoId(100L).email("creator@test.com").nickname("creator").role(Role.USER)
                        .build());

        // GK 1자리 슬롯으로 매치 생성 (슬롯을 첫 저장 전에 추가하여 cascade 한 번에 처리)
        MatchEntity matchToSave = MatchEntity.builder()
                .member(creator)
                .title("Concurrency Test Match")
                .content("Test Content")
                .placeName("Test Place")
                .district("Test District")
                .matchDate(LocalDateTime.now().plusDays(1))
                .status(MatchStatus.RECRUITING)
                .build();

        matchToSave.getSlots().add(MatchPositionSlot.of(matchToSave, Position.GK, 1));
        testMatch = matchRepository.save(matchToSave);
    }

    @AfterEach
    void tearDown() {
        applicationRepository.deleteAllInBatch();
        matchPositionSlotRepository.deleteAllInBatch();
        matchRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("100명의 유저가 GK 1자리에 동시 신청할 때, 1명만 성공해야 한다.")
    void applyMatch_Concurrency_GK_One_Slot() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

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

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    applicationService.applyMatch(testMatch.getId(), applicantIds[index], Position.GK);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // GK 1자리이므로 1명만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(applicationRepository.count()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);
    }
}
