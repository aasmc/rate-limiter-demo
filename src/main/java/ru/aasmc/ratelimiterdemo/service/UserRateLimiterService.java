package ru.aasmc.ratelimiterdemo.service;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.aasmc.ratelimiterdemo.exception.ServiceException;
import ru.aasmc.ratelimiterdemo.storage.model.TimestampRange;
import ru.aasmc.ratelimiterdemo.storage.repository.UserRateLimiterRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserRateLimiterService {

    private static final String RATE_LIMITER_METRIC = "rate_limiter_event";
    private static final String RATE_LIMITER_DURATION_METRIC = "rate_limiter_duration";
    private static final String USER_TAG = "user";
    private static final String STATUS_TAG = "status";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "failure";

    private final UserRateLimiterRepository userRateLimiterRepository;
    private final MeterRegistry meterRegistry;
    @Value("${internal.allowed-request-period}")
    private Duration allowedRequestPeriod;

    @Timed(value = RATE_LIMITER_DURATION_METRIC, histogram = true, percentiles = {0.95, 0.99})
    public void permitRequestOrThrow(String userName) {
        try {
            log.info("Trying to acquire permit on userRateLimiter for user {}", userName);
            TimestampRange range = new TimestampRange(OffsetDateTime.now(), OffsetDateTime.now().plus(allowedRequestPeriod));
            userRateLimiterRepository.insert(userName, Instant.now(), range.toString());
            log.info("Successfully acquired permit on userRateLimiter for user {}", userName);
            registerMetric(userName, STATUS_SUCCESS);
        } catch (DataIntegrityViolationException ex) {
            log.error(ex.getMessage());
            registerMetric(userName, STATUS_ERROR);
            throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request. Error " + ex.getMessage());
        }
    }

    private void registerMetric(String user, String status) {
        meterRegistry.counter(RATE_LIMITER_METRIC, List.of(Tag.of(USER_TAG, user), Tag.of(STATUS_TAG, status))).increment();
    }

}

