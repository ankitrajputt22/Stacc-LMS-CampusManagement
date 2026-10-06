package com.stacc.backend.lms.membership;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsStudentMembershipRepository extends JpaRepository<LmsStudentMembership, Long> {

    Optional<LmsStudentMembership> findByCourseEnrollmentId(Long courseEnrollmentId);
}
