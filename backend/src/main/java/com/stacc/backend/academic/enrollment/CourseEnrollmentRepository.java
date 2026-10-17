package com.stacc.backend.academic.enrollment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    Optional<CourseEnrollment> findBySemesterEnrollmentIdAndCourseOfferingId(
            Long semesterEnrollmentId, Long courseOfferingId);

    /** The enrollments of one course offering in one status, such as everyone currently enrolled. */
    List<CourseEnrollment> findAllByCourseOfferingIdAndStatus(Long courseOfferingId, CourseEnrollmentStatus status);
}
