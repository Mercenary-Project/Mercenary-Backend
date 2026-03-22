package org.example.mercenary.domain.match.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredMatchCleanupScheduler {

    private final MatchService matchService;
    private final Clock appClock;

    @Scheduled(
            cron = "${app.match.cleanup-cron:0 */10 * * * *}",
            zone = "${app.timezone:Asia/Seoul}"
    )
    public void cleanupExpiredMatches() {
        int deletedCount = matchService.deleteExpiredMatches(LocalDateTime.now(appClock));

        if (deletedCount > 0) {
            log.info("Deleted {} expired matches", deletedCount);
        }
    }
}
