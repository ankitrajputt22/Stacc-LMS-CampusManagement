package com.stacc.backend.lms.management;

import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseHomeView;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LmsCourseManagementService {

    static final String OFFERING_NOT_FOUND = "Course offering not found.";
    static final String COURSE_NOT_FOUND = "LMS course not found.";
    static final String ALREADY_EXISTS = "An LMS course already exists for this course offering.";
    static final String ARCHIVED_NOT_PUBLISHABLE = "An archived LMS course cannot be published.";
    static final String ARCHIVED_NOT_CHANGEABLE = "An archived LMS course cannot be changed.";

    private final CourseOfferingRepository courseOfferings;
    private final LmsCourseRepository lmsCourses;
    private final FacultyTeachingAccessService teachingAccess;
    private final CourseEnrollmentRepository courseEnrollments;

    public LmsCourseManagementService(CourseOfferingRepository courseOfferings, LmsCourseRepository lmsCourses,
            FacultyTeachingAccessService teachingAccess, CourseEnrollmentRepository courseEnrollments) {
        this.courseOfferings = courseOfferings;
        this.lmsCourses = lmsCourses;
        this.teachingAccess = teachingAccess;
        this.courseEnrollments = courseEnrollments;
    }

    public LmsCourseChange create(Long userAccountId, Long courseOfferingId) {
        if (!teachingAccess.canTeachCourseOffering(userAccountId, courseOfferingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, OFFERING_NOT_FOUND);
        }
        CourseOffering courseOffering = courseOfferings.findById(courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, OFFERING_NOT_FOUND));
        if (lmsCourses.findByCourseOfferingId(courseOffering.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_EXISTS);
        }
        LmsCourse lmsCourse;
        try {
            lmsCourse = lmsCourses.saveAndFlush(new LmsCourse(courseOffering));
        } catch (DataIntegrityViolationException clash) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_EXISTS);
        }
        return changeOf(lmsCourse, currentStudents(lmsCourse));
    }

    public LmsCourseChange publish(Long userAccountId, Long lmsCourseId) {
        LmsCourse lmsCourse = lockManagedCourse(userAccountId, lmsCourseId);
        if (lmsCourse.getStatus() == LmsCourseStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, ARCHIVED_NOT_PUBLISHABLE);
        }
        lmsCourse.publish();
        return changeOf(lmsCourse, currentStudents(lmsCourse));
    }

    public LmsCourseChange archive(Long userAccountId, Long lmsCourseId) {
        LmsCourse lmsCourse = lockManagedCourse(userAccountId, lmsCourseId);
        lmsCourse.archive();
        return changeOf(lmsCourse, null);
    }

    public LmsCourse changeHome(Long userAccountId, Long lmsCourseId, LmsCourseHomeView homeView, String welcomeMessage) {
        LmsCourse lmsCourse = lockManagedCourse(userAccountId, lmsCourseId);
        if (lmsCourse.getStatus() == LmsCourseStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, ARCHIVED_NOT_CHANGEABLE);
        }
        lmsCourse.changeHome(homeView, welcomeMessage);
        return lmsCourse;
    }

    private LmsCourse lockManagedCourse(Long userAccountId, Long lmsCourseId) {
        LmsCourse lmsCourse = lmsCourses.findByIdForUpdate(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND));
        if (!teachingAccess.canTeachCourseOffering(userAccountId, lmsCourse.getCourseOffering().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND);
        }
        return lmsCourse;
    }

    private int currentStudents(LmsCourse lmsCourse) {
        return courseEnrollments.findAllCurrentByCourseOfferingId(lmsCourse.getCourseOffering().getId()).size();
    }

    private static LmsCourseChange changeOf(LmsCourse lmsCourse, Integer currentStudentCount) {
        return new LmsCourseChange(lmsCourse.getId(), lmsCourse.getCourseOffering().getId(), lmsCourse.getStatus(),
                currentStudentCount);
    }
}
