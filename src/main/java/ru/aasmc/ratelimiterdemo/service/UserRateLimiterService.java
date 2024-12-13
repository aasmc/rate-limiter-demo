package ru.aasmc.ratelimiterdemo.service;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.relational.core.conversion.DbActionExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.aasmc.ratelimiterdemo.exception.ServiceException;
import ru.aasmc.ratelimiterdemo.storage.model.UserRateLimiter;
import ru.aasmc.ratelimiterdemo.storage.repository.UserRateLimiterRepository;

import java.time.Duration;
import java.time.Instant;
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
    public void acquire(String userName) {
        userRateLimiterRepository.acquireToken(userName, allowedRequestPeriod, Instant.now())
                .orElseThrow(() -> {
                    registerMetric(userName, STATUS_ERROR);
                    return new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request");
                });
        registerMetric(userName, STATUS_SUCCESS);
    }

    @Timed(value = RATE_LIMITER_DURATION_METRIC, histogram = true, percentiles = {0.95, 0.99})
    @Transactional
    public void permitRequestOrThrow(String userName) {
        try {
            permitRequestOrThrowInternal(userName);
        } catch (UncategorizedSQLException ex) {
            // thrown when select for update no wait doesn't allow to proceed
            log.error(ex.getMessage());
            registerMetric(userName, STATUS_ERROR);
            throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS,  "Cannot allow request. Error " + ex.getMessage());
        }
    }

    private void permitRequestOrThrowInternal(String userName) {
        userRateLimiterRepository.findByUserIdForUpdateNoWait(userName)
                .ifPresentOrElse(
                        rl -> {
                            Instant now = Instant.now();
                            if (allowedToProceed(rl, now)) {
                                rl.setRequestTime(now);
                                userRateLimiterRepository.save(rl);
                                log.info("Request permitted");
                                registerMetric(userName, STATUS_SUCCESS);
                            } else {
                                log.error("Request not permitted");
                                registerMetric(userName, STATUS_ERROR);
                                throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request.");
                            }
                        },
                        () -> {
                            UserRateLimiter rl = new UserRateLimiter(null, userName, Instant.now());
                            try {
                                log.info("Trying to permit request.");
                                userRateLimiterRepository.save(rl);
                                registerMetric(userName, STATUS_SUCCESS);
                            } catch (DbActionExecutionException ex) {
                                log.error("Failed to permit request. Exception = {}", ex.getMessage());
                                registerMetric(userName, STATUS_ERROR);
                                throw new ServiceException(HttpStatus.TOO_MANY_REQUESTS, "Cannot allow request. Error " + ex.getMessage());
                            }
                        }
                );
    }

    private boolean allowedToProceed(UserRateLimiter rl, Instant now) {
        //       request_dt   (now - allowedRequestPeriod)    now
        // time: ___|_________________|_____________________|_____
        return now.minus(allowedRequestPeriod).isAfter(rl.getRequestTime());
    }

    private void registerMetric(String user, String status) {
        meterRegistry.counter(RATE_LIMITER_METRIC, List.of(Tag.of(USER_TAG, user), Tag.of(STATUS_TAG, status))).increment();
    }

}

