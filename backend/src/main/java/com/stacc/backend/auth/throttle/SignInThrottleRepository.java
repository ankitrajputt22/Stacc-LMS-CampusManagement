package com.stacc.backend.auth.throttle;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignInThrottleRepository extends JpaRepository<SignInThrottle, Long> {

    boolean existsByScopeAndKeyHash(SignInThrottle.Scope scope, String keyHash);

    /** Makes sure the key has a row; two requests at once never create two. */
    @Modifying
    @Query(value = "insert ignore into sign_in_throttles (scope, key_hash, failures, updated_at) "
            + "values (:scope, :keyHash, 0, :now)", nativeQuery = true)
    void ensure(@Param("scope") String scope, @Param("keyHash") String keyHash, @Param("now") Instant now);

    /** The key's row, locked until the transaction ends, so concurrent failures are counted one after another. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select throttle from SignInThrottle throttle where throttle.scope = :scope and throttle.keyHash = :keyHash")
    Optional<SignInThrottle> findForUpdate(@Param("scope") SignInThrottle.Scope scope, @Param("keyHash") String keyHash);

    @Query("select throttle from SignInThrottle throttle where throttle.keyHash in :keyHashes")
    List<SignInThrottle> findAllByKeyHashIn(@Param("keyHashes") Collection<String> keyHashes);
}
