package com.stacc.backend.pass;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PassRepository extends JpaRepository<Pass, Long> {

    Optional<Pass> findByCodeFingerprint(String codeFingerprint);

    @Query("select p from Pass p where p.purpose = :purpose and p.sourceId = :sourceId and p.revokedAt is null")
    List<Pass> findOpen(@Param("purpose") PassPurpose purpose, @Param("sourceId") Long sourceId);
}
