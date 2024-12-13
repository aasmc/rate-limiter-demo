package ru.aasmc.ratelimiterdemo.storage.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.aasmc.ratelimiterdemo.storage.model.UserRateLimiter;

import java.util.Optional;


@Repository
public interface UserRateLimiterRepository extends CrudRepository<UserRateLimiter, Long> {

    @Query("select * from user_ratelimiter where user_name = :userName for update nowait")
    Optional<UserRateLimiter> findByUserIdForUpdateNoWait(String userName);

}
