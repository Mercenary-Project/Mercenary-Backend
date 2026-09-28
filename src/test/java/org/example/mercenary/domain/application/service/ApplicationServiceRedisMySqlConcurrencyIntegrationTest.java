package org.example.mercenary.domain.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManagerFactory;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.mercenary.domain.application.entity.ApplicationEntity;
import org.example.mercenary.domain.application.entity.ApplicationStatus;
import org.example.mercenary.domain.application.repository.ApplicationRepository;
import org.example.mercenary.domain.common.Position;
import org.example.mercenary.domain.match.entity.MatchEntity;
import org.example.mercenary.domain.match.entity.MatchPositionSlot;
import org.example.mercenary.domain.match.repository.MatchPositionSlotRepository;
import org.example.mercenary.domain.match.repository.MatchRepository;
import org.example.mercenary.domain.member.entity.MemberEntity;
import org.example.mercenary.domain.member.entity.Role;
import org.example.mercenary.domain.member.repository.MemberRepository;
import org.example.mercenary.global.exception.ConflictException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest
class ApplicationServiceRedisMySqlConcurrencyIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mercenary_test")
            .withUsername("mercenary")
            .withPassword("mercenary");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> "test-jwt-secret-key-with-enough-length-1234567890");
        registry.add("kakao.client-id", () -> "test-kakao-client-id");
        registry.add("kakao.redirect-uri", () -> "http://localhost:5173/login/callback");
    }

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private MatchPositionSlotRepository matchPositionSlotRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MatchEntity match;
    private Long ownerId;
    private List<Long> applicantIds;

    @BeforeEach
    void setUp() {
        MemberEntity owner = memberRepository.save(MemberEntity.builder()
                .kakaoId(1L)
                .email("owner@example.com")
                .nickname("owner")
                .role(Role.USER)
                .build());

        ownerId = owner.getId();
        MatchEntity newMatch = MatchEntity.builder()
                .member(owner)
                .title("ST 9-seat match")
                .content("integration test")
                .placeName("test ground")
                .district("test district")
                .matchDate(LocalDateTime.now().plusDays(1))
                .build();
        newMatch.getSlots().add(MatchPositionSlot.of(newMatch, Position.ST, 9));
        match = matchRepository.save(newMatch);

        applicantIds = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            applicantIds.add(memberRepository.save(MemberEntity.builder()
                    .kakaoId(10_000L + index)
                    .email("applicant-" + index + "@example.com")
                    .nickname("applicant-" + index)
                    .role(Role.USER)
                    .build()).getId());
        }
    }

    @AfterEach
    void tearDown() {
        applicationRepository.deleteAllInBatch();
        matchPositionSlotRepository.deleteAllInBatch();
        matchRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("주최자 승인 정책은 ST 9석이어도 승인 전 신청 100건을 모두 READY로 보관한다")
    void hostApproval_keepsOneHundredPendingApplicationsBeforeAnyDecision() {
        List<Exception> failures = new ArrayList<>();

        for (Long applicantId : applicantIds) {
            try {
                applicationService.applyMatch(match.getId(), applicantId, Position.ST);
            } catch (Exception exception) {
                failures.add(exception);
            }
        }

        assertThat(failures).isEmpty();
        assertThat(applicationRepository.count()).isEqualTo(100);
        assertThat(applicationRepository.findAll())
                .allMatch(application -> application.getStatus() == ApplicationStatus.READY);
    }

    @Test
    @DisplayName("Flyway V7은 기존 VARCHAR 승인 정책 컬럼을 enum으로 변환해 운영 스키마 검증을 통과시킨다")
    void approvalPolicyV7Migration_convertsVarcharForProductionSchemaValidation() {
        jdbcTemplate.execute("""
                ALTER TABLE matches
                MODIFY COLUMN approval_policy VARCHAR(20) NOT NULL DEFAULT 'HOST_APPROVAL'
                """);
        Flyway.configure()
                .dataSource(jdbcTemplate.getDataSource())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("6")
                .load()
                .migrate();

        LocalContainerEntityManagerFactoryBean validator = new LocalContainerEntityManagerFactoryBean();
        validator.setDataSource(jdbcTemplate.getDataSource());
        validator.setPackagesToScan("org.example.mercenary");
        validator.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> validationProperties = new HashMap<>(entityManagerFactory.getProperties());
        validationProperties.put("hibernate.hbm2ddl.auto", "validate");
        validator.setJpaPropertyMap(validationProperties);

        try {
            assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
        } finally {
            validator.destroy();
        }
    }

    @Test
    @DisplayName("자동 승인 정책의 ST 9석에는 100명 동시 신청 중 정확히 9명만 APPROVED 된다")
    void autoApproval_capsConcurrentApplicationsAtPositionCapacity() throws InterruptedException {
        setApprovalPolicy(match, "AUTO_APPROVE");

        ConcurrentResult result = runConcurrently(applicantIds, applicantId ->
                applicationService.applyMatch(match.getId(), applicantId, Position.ST));

        assertThat(result.successCount()).isEqualTo(9);
        assertThat(result.failures()).hasSize(91);
        assertThat(applicationRepository.findAll())
                .filteredOn(application -> application.getStatus() == ApplicationStatus.APPROVED)
                .hasSize(9);
        assertThat(matchPositionSlotRepository.findByMatchAndPosition(match, Position.ST).orElseThrow().getFilled())
                .isEqualTo(9);
    }

    @Test
    @DisplayName("같은 사용자의 같은 매치·포지션 100개 동시 신청은 신청 레코드 하나만 남긴다")
    void duplicateConcurrentApplications_createOnlyOneRecord() throws InterruptedException {
        Long applicantId = applicantIds.get(0);

        ConcurrentResult result = runConcurrently(Collections.nCopies(100, applicantId), id ->
                applicationService.applyMatch(match.getId(), id, Position.ST));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failures()).hasSize(99);
        assertThat(applicationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("주최자 승인 정책은 READY 100건의 동시 승인에서 ST 정원 9건만 APPROVED 한다")
    void hostApproval_capsConcurrentDecisionsAtPositionCapacity() throws InterruptedException {
        applicantIds.forEach(applicantId -> applicationService.applyMatch(match.getId(), applicantId, Position.ST));
        List<Long> applicationIds = applicationRepository.findAll().stream()
                .map(ApplicationEntity::getId)
                .toList();

        ConcurrentResult result = runConcurrently(applicationIds, applicationId ->
                applicationService.updateApplicationStatus(
                        match.getId(), applicationId, ownerId, ApplicationStatus.APPROVED));

        assertThat(result.successCount()).isEqualTo(9);
        assertThat(result.failures()).hasSize(91);
        assertThat(applicationRepository.findAll())
                .filteredOn(application -> application.getStatus() == ApplicationStatus.APPROVED)
                .hasSize(9);
        assertThat(matchPositionSlotRepository.findByMatchAndPosition(match, Position.ST).orElseThrow().getFilled())
                .isEqualTo(9);
    }

    @Test
    @DisplayName("동일 신청의 승인과 취소 경쟁을 반복해도 최종 상태와 포지션 인원 수가 일치한다")
    void approvalAndCancellationRace_leavesNoPendingApplicationOrCountMismatch() throws InterruptedException {
        Long applicantId = applicantIds.get(0);
        applicationService.applyMatch(match.getId(), applicantId, Position.ST);
        Long applicationId = applicationRepository.findByMatchAndUserId(match, applicantId).orElseThrow().getId();

        List<ThrowingRunnable> actions = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            actions.add(() -> applicationService.updateApplicationStatus(
                    match.getId(), applicationId, ownerId, ApplicationStatus.APPROVED));
            actions.add(() -> applicationService.cancelApplication(match.getId(), applicantId));
        }

        runConcurrentActions(actions);

        ApplicationEntity application = applicationRepository.findById(applicationId).orElseThrow();
        assertThat(application.getStatus()).isIn(ApplicationStatus.APPROVED, ApplicationStatus.CANCELED);
        assertThat(matchPositionSlotRepository.findByMatchAndPosition(match, Position.ST).orElseThrow().getFilled())
                .isEqualTo(application.getStatus() == ApplicationStatus.APPROVED ? 1 : 0);
    }

    @Test
    @DisplayName("만료된 매치에서는 신청과 READY 신청의 승인 모두 성공하지 않는다")
    void expiredMatch_rejectsApplicationAndApproval() {
        MatchEntity expiredMatch = MatchEntity.builder()
                .member(memberRepository.findById(ownerId).orElseThrow())
                .title("expired match")
                .content("integration test")
                .placeName("test ground")
                .district("test district")
                .matchDate(LocalDateTime.now().minusMinutes(1))
                .build();
        expiredMatch.getSlots().add(MatchPositionSlot.of(expiredMatch, Position.ST, 9));
        expiredMatch = matchRepository.save(expiredMatch);
        Long expiredMatchId = expiredMatch.getId();
        Long applicantId = applicantIds.get(0);
        ApplicationEntity readyApplication = applicationRepository.save(ApplicationEntity.builder()
                .match(expiredMatch)
                .userId(applicantId)
                .position(Position.ST)
                .status(ApplicationStatus.READY)
                .build());

        assertThatThrownBy(() -> applicationService.applyMatch(expiredMatchId, applicantIds.get(1), Position.ST))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> applicationService.updateApplicationStatus(
                expiredMatchId, readyApplication.getId(), ownerId, ApplicationStatus.APPROVED))
                .isInstanceOf(ConflictException.class);
        assertThat(applicationRepository.findById(readyApplication.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.READY);
        assertThat(matchPositionSlotRepository.findByMatchAndPosition(expiredMatch, Position.ST).orElseThrow().getFilled())
                .isZero();
    }

    private ConcurrentResult runConcurrently(List<Long> memberIds, ThrowingLongConsumer action)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(memberIds.size());
        CountDownLatch ready = new CountDownLatch(memberIds.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(memberIds.size());
        AtomicInteger successCount = new AtomicInteger();
        ConcurrentLinkedQueue<Exception> failures = new ConcurrentLinkedQueue<>();

        for (Long memberId : memberIds) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.accept(memberId);
                    successCount.incrementAndGet();
                } catch (Exception exception) {
                    failures.add(exception);
                } finally {
                    complete.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(complete.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        return new ConcurrentResult(successCount.get(), new ArrayList<>(failures));
    }

    private void runConcurrentActions(List<ThrowingRunnable> actions) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(actions.size());

        for (ThrowingRunnable action : actions) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    action.run();
                } catch (Exception ignored) {
                } finally {
                    complete.countDown();
                }
            });
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(complete.await(30, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }

    private void setApprovalPolicy(MatchEntity target, String policyName) {
        try {
            Class<?> policyType = Class.forName("org.example.mercenary.domain.match.entity.ApprovalPolicy");
            Object policy = Enum.valueOf(policyType.asSubclass(Enum.class), policyName);
            var field = MatchEntity.class.getDeclaredField("approvalPolicy");
            field.setAccessible(true);
            field.set(target, policy);
            matchRepository.saveAndFlush(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("자동 승인 정책을 매치에 설정할 수 있어야 합니다.", exception);
        }
    }

    private record ConcurrentResult(int successCount, List<Exception> failures) {
    }

    @FunctionalInterface
    private interface ThrowingLongConsumer {
        void accept(Long value) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
