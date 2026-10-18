package com.stacc.backend.lms.management;

import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.lms.membership.LmsStudentMembershipProvisioningService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty create, publish, and archive the LMS course of a course offering they teach, and synchronize its students'
 * memberships. Who may do so comes from {@link FacultyTeachingAccessService} for the offering, found from the stored LMS
 * course and never from the client, so an archived course stays manageable while the normal faculty access policy
 * refuses it. Anything the faculty member does not teach answers the same 404 as a missing course.
 *
 * <p>A new LMS course starts {@code DRAFT}. Publishing moves a draft to {@code PUBLISHED}; archiving moves either to
 * {@code ARCHIVED}, which is final: nothing restores it, and nothing is ever deleted. Creating and publishing also
 * give every current student of the offering an active membership through
 * {@link LmsStudentMembershipProvisioningService}, in the same transaction, so a consistency error leaves nothing
 * half done. Course offerings, enrollments, and teaching assignments are only read.
 */
@Service
@Transactional
public class LmsCourseManagementService {

    static final String OFFERING_NOT_FOUND = "Course offering not found.";
    static final String COURSE_NOT_FOUND = "LMS course not found.";
    static final String ALREADY_EXISTS = "An LMS course already exists for this course offering.";
    static final String ARCHIVED_NOT_PUBLISHABLE = "An archived LMS course cannot be published.";
    static final String ARCHIVED_NOT_SYNCHRONIZED = "Student access cannot be synchronized for an archived LMS course.";

    private final CourseOfferingRepository courseOfferings;
    private final LmsCourseRepository lmsCourses;
    private final FacultyTeachingAccessService teachingAccess;
    private final LmsStudentMembershipProvisioningService provisioning;

    public LmsCourseManagementService(CourseOfferingRepository courseOfferings, LmsCourseRepository lmsCourses,
            FacultyTeachingAccessService teachingAccess, LmsStudentMembershipProvisioningService provisioning) {
        this.courseOfferings = courseOfferings;
        this.lmsCourses = lmsCourses;
        this.teachingAccess = teachingAccess;
        this.provisioning = provisioning;
    }

    /**
     * Creates the draft LMS course of a course offering the faculty member teaches, and gives its current students
     * active memberships. An offering already has at most one LMS course: a second one answers 409, also when the
     * database's unique constraint catches two requests at once.
     *
     * @param userAccountId the ID of the signed-in account, taken from its access token
     */
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
        return changeOf(lmsCourse, provisioning.provisionCurrentEnrollments(lmsCourse));
    }

    /** Gives every current student of a draft or published LMS course an active membership. Safe to repeat. */
    public LmsCourseChange synchronizeStudentMemberships(Long userAccountId, Long lmsCourseId) {
        LmsCourse lmsCourse = lockManagedCourse(userAccountId, lmsCourseId);
        if (lmsCourse.getStatus() == LmsCourseStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, ARCHIVED_NOT_SYNCHRONIZED);
        }
        return changeOf(lmsCourse, provisioning.provisionCurrentEnrollments(lmsCourse));
    }

    /**
     * Publishes a draft LMS course after synchronizing its memberships; a published one stays published and is
     * synchronized again. Students still get in only through the student access policy.
     */
    public LmsCourseChange publish(Long userAccountId, Long lmsCourseId) {
        LmsCourse lmsCourse = lockManagedCourse(userAccountId, lmsCourseId);
        if (lmsCourse.getStatus() == LmsCourseStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, ARCHIVED_NOT_PUBLISHABLE);
        }
        int synchronizedCount = provisioning.provisionCurrentEnrollments(lmsCourse);
        lmsCourse.publish();
        return changeOf(lmsCourse, synchronizedCount);
    }

    /**
     * Archives a draft or published LMS course; an archived one stays archived. Its content, assignments, submissions,
     * grades, and memberships are all kept. Students and the normal faculty pages lose access to it.
     */
    public LmsCourseChange archive(Long userAccountId, Long lmsCourseId) {
        LmsCourse lmsCourse = lockManagedCourse(userAccountId, lmsCourseId);
        lmsCourse.archive();
        return changeOf(lmsCourse, null);
    }

    // The course is locked first, so its status cannot change under a lifecycle step, then its offering decides who
    // may manage it. A course the faculty member does not teach looks exactly like a missing one.
    private LmsCourse lockManagedCourse(Long userAccountId, Long lmsCourseId) {
        LmsCourse lmsCourse = lmsCourses.findByIdForUpdate(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND));
        if (!teachingAccess.canTeachCourseOffering(userAccountId, lmsCourse.getCourseOffering().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND);
        }
        return lmsCourse;
    }

    private static LmsCourseChange changeOf(LmsCourse lmsCourse, Integer synchronizedCount) {
        return new LmsCourseChange(lmsCourse.getId(), lmsCourse.getCourseOffering().getId(), lmsCourse.getStatus(),
                synchronizedCount);
    }
}
