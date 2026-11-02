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

/**
 * Answers one question: may the student behind this account use this LMS course right now?
 *
 * <p>The answer comes only from the official enrollment and the course's state, read live, starting from the signed-in
 * account. It is a plain yes or no, so a caller cannot learn why access was refused. Checking access never creates or
 * changes anything. Dropping or withdrawing a course, an inactive profile, or an archived course ends access at once,
 * everywhere that asks here.
 */
@Service
public class LmsStudentAccessService {

    private final LmsCourseRepository lmsCourses;
    private final CourseEnrollmentRepository courseEnrollments;

    public LmsStudentAccessService(LmsCourseRepository lmsCourses, CourseEnrollmentRepository courseEnrollments) {
        this.lmsCourses = lmsCourses;
        this.courseEnrollments = courseEnrollments;
    }

    /**
     * True only when {@link LmsStudentAccessPolicy} grants the student behind this account the LMS course now.
     * Anything else, including an unknown account or course, is false.
     *
     * @param userAccountId the ID of the signed-in account, taken from its access token and never from anything the
     *                      client sends in the request
     */
    @Transactional(readOnly = true)
    public boolean canAccessCourse(Long userAccountId, Long lmsCourseId) {
        return findAccessGrantingEnrollment(userAccountId, lmsCourseId).isPresent();
    }

    /**
     * The official course enrollment through which the student behind this account may use the LMS course right now,
     * by exactly the rule of {@link #canAccessCourse}. It is the student's place in the course, for features that act
     * on the student's own records. Empty whenever access is not granted.
     *
     * @param userAccountId the ID of the signed-in account, taken from its access token and never from anything the
     *                      client sends in the request
     */
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

    /**
     * The accounts of every student who may use the LMS course right now, by exactly the rule of
     * {@link #canAccessCourse}, in one query. It only reads, and is meant for telling those students about a change in
     * the course; it grants nothing.
     */
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
