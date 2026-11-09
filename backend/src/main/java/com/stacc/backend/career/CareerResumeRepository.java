package com.stacc.backend.career;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerResumeRepository extends JpaRepository<CareerResume, Long> {

    List<CareerResume> findAllByAccountIdOrderByCreatedAtDesc(Long accountId);
}
