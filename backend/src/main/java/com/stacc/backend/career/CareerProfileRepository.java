package com.stacc.backend.career;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerProfileRepository extends JpaRepository<CareerProfile, Long> {

    Optional<CareerProfile> findByAccountId(Long accountId);
}
