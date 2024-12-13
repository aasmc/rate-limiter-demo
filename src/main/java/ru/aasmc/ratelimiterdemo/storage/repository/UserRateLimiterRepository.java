package ru.aasmc.ratelimiterdemo.storage.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.aasmc.ratelimiterdemo.storage.model.UserRateLimiter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;


@Repository
public interface UserRateLimiterRepository extends CrudRepository<UserRateLimiter, Long> {

    @Query("select * from user_ratelimiter where user_name = :userName for update nowait")
    Optional<UserRateLimiter> findByUserIdForUpdateNoWait(String userName);

    @Query("""
            insert into user_ratelimiter(user_name, request_dt)
            values (:userName, :now)
            on conflict(user_name) do update
            set request_dt = excluded.request_dt
            where excluded.request_dt - :allowedRequestPeriod::interval > user_ratelimiter.request_dt
            returning *;
            """)
    Optional<UserRateLimiter> acquireToken(String userName, Duration allowedRequestPeriod, Instant now);
}
