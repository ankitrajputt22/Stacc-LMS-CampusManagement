package com.stacc.backend.academic.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {

    Optional<AcademicSession> findByCode(String code);

    /** Every academic session, the latest start first. */
    List<AcademicSession> findAllByOrderByStartDateDescIdDesc();

    // Compares as the unique constraint does, in the column's collation.
    boolean existsByCode(String code);
}
