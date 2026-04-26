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

    @Autowired
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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

   // 1. 기존 메서드 수정: "신청"은 이제 100명 모두 성공해야 합니다.
    @Test
    @DisplayName("100명의 유저가 GK 1자리에 동시 신청할 때, 100명 모두 대기(READY) 상태로 성공해야 한다.")
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

        // 🚨 수정된 부분: 이제 100명 모두 대기 상태로 저장이 성공해야 합니다.
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);
        assertThat(applicationRepository.count()).isEqualTo(100);
    }

    // 2. 🌟 새로 추가할 메서드: "승인" 할 때 초과 인원을 막는지 검증합니다. (이게 진짜 동시성 테스트입니다!)
    @Test
    @DisplayName("방장이 100명의 대기자를 동시에 승인 처리할 때, 빈자리(1개)만큼만 성공하고 99명은 실패해야 한다.")
    void approveMatch_Concurrency_GK_One_Slot() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 100명의 유저와 대기(READY) 상태의 신청서 미리 생성
        Long[] applicationIds = new Long[threadCount];
        for (int i = 0; i < threadCount; i++) {
            MemberEntity member = memberRepository.save(
                    MemberEntity.builder()
                            .kakaoId(300L + i) // 기존 테스트와 겹치지 않게 300번대 사용
                            .email("ready" + i + "@test.com")
                            .nickname("ready" + i)
                            .role(Role.USER)
                            .build());

            org.example.mercenary.domain.application.entity.ApplicationEntity application = applicationRepository.save(
                    org.example.mercenary.domain.application.entity.ApplicationEntity.builder()
                            .match(testMatch)
                            .userId(member.getId())
                            .position(Position.GK)
                            .status(org.example.mercenary.domain.application.entity.ApplicationStatus.READY)
                            .build());
            applicationIds[i] = application.getId();
        }

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        Long creatorId = testMatch.getMember().getId(); // 방장의 ID

        // 방장이 100개의 신청서를 동시에 승인(APPROVED) 시도
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    applicationService.updateApplicationStatus(
                            testMatch.getId(), 
                            applicationIds[index], 
                            creatorId, 
                            org.example.mercenary.domain.application.entity.ApplicationStatus.APPROVED
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // 빈자리가 1개이므로 1명만 승인 성공하고, 99번은 마감 예외가 발생해야 정상입니다.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);
        
       // TransactionTemplate으로 감싸서 영속성 컨텍스트(세션)를 유지한 상태로 Lazy 로딩 수행
        transactionTemplate.executeWithoutResult(status -> {
            MatchEntity updatedMatch = matchRepository.findById(testMatch.getId()).orElseThrow();
            assertThat(updatedMatch.getSlot(Position.GK).getFilled()).isEqualTo(1);
        });
    }
}
