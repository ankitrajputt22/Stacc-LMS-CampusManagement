package com.stacc.backend.lms.assignment.submission;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsSubmissionAttemptRepository extends JpaRepository<LmsSubmissionAttempt, Long> {

    /** Every attempt of one submission, newest first. */
    List<LmsSubmissionAttempt> findAllBySubmissionIdOrderByAttemptNumberDesc(Long submissionId);
}
