package com.stacc.backend.lms.access;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsStudentAccessService {

    private final LmsCourseRepository lmsCourses;
    private final CourseEnrollmentRepository courseEnrollments;

    public LmsStudentAccessService(LmsCourseRepository lmsCourses, CourseEnrollmentRepository courseEnrollments) {
        this.lmsCourses = lmsCourses;
        this.courseEnrollments = courseEnrollments;
    }

    @Transactional(readOnly = true)
    public boolean canAccessCourse(Long userAccountId, Long lmsCourseId) {
        return findAccessGrantingEnrollment(userAccountId, lmsCourseId).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<CourseEnrollment> findAccessGrantingEnrollment(Long userAccountId, Long lmsCourseId) {
        if (userAccountId == null || lmsCourseId == null) {
            return Optional.empty();
        }
        Optional<LmsCourse> lmsCourse = lmsCourses.findById(lmsCourseId)
                .filter(course -> course.getStatus() == LmsCourseStatus.PUBLISHED);
        if (lmsCourse.isEmpty()) {
            return Optional.empty();
        }
        Long courseOfferingId = lmsCourse.get().getCourseOffering().getId();
        return courseEnrollments.findAllWithStudentByUserAccountIdAndCourseOfferingId(userAccountId, courseOfferingId)
                .stream()
                .filter(enrollment -> LmsStudentAccessPolicy.grantsCurrentAccess(enrollment, lmsCourse.get(), userAccountId))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<Long> findStudentAccountIdsWithAccess(Long lmsCourseId) {
        if (lmsCourseId == null) {
            return List.of();
        }
        Optional<LmsCourse> lmsCourse = lmsCourses.findById(lmsCourseId)
                .filter(course -> course.getStatus() == LmsCourseStatus.PUBLISHED);
        if (lmsCourse.isEmpty()) {
            return List.of();
        }
        return courseEnrollments.findAllCurrentByCourseOfferingId(lmsCourse.get().getCourseOffering().getId()).stream()
                .map(enrollment -> enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount().getId())
                .distinct()
                .toList();
    }
}
