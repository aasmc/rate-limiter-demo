package ru.aasmc.ratelimiterdemo.scheduler;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.aasmc.ratelimiterdemo.storage.repository.UserRateLimiterRepository;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "scheduling.ratelimiter-cleaner.enabled", havingValue = "true")
public class RateLimiterCleanerJob {

    private static final String TABLE_NAME = "user_ratelimiter";
    private static final String METRIC_NAME = "rate_limiter_clean_count";

    private final UserRateLimiterRepository repository;
    private final MeterRegistry meterRegistry;
    @Value("${scheduling.ratelimiter-cleaner.clean-before-period}")
    private Duration cleanBeforePeriod = Duration.ofMinutes(5);

    @Scheduled(cron = "${scheduling.ratelimiter-cleaner.cron}", scheduler = "rateLimiterScheduler")
    @SchedulerLock(name = "${scheduling.ratelimiter-cleaner.job-name}",
            lockAtLeastFor = "${scheduling.ratelimiter-cleaner.lock-at-least-for}",
            lockAtMostFor = "${scheduling.ratelimiter-cleaner.lock-at-most-for}")
    public void doClean() {
        LockAssert.assertLocked();
        long beforeCleanCount = repository.count();
        log.info("Cleaning of table {} started. Number of rows is {}", TABLE_NAME, beforeCleanCount);
        repository.deleteBefore(Instant.now().minus(cleanBeforePeriod));
        long afterCleanCount = repository.count();
        log.info("Cleaning of table {} finished. Number of rows is {}", TABLE_NAME, afterCleanCount);
        meterRegistry.counter(METRIC_NAME).increment(beforeCleanCount - afterCleanCount);
    }

}
