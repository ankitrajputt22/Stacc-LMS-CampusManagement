package com.stacc.backend.identity.faculty;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FacultyProfileRepository extends JpaRepository<FacultyProfile, Long> {

    Optional<FacultyProfile> findByUserAccountId(Long userAccountId);
}
