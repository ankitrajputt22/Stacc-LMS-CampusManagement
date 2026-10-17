package com.stacc.backend.academic.result;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty record and publish official course results here. Every action starts from the signed-in account and
 * checks {@link FacultyTeachingAccessService} for the course offering in the route; the enrollment must belong to
 * that offering, so an enrollment ID alone never gives access. The acting faculty profile comes from that check. An
 * offering or enrollment the caller may not use answers the same 404 as one that does not exist. No LMS data is
 * read or changed, and nothing is calculated from LMS grades or attendance.
 *
 * <p>Results are never deleted or unpublished.
 */
@Service
@Transactional
public class CourseResultManagementService {

    static final String COURSE_UNAVAILABLE = "Course offering not found or unavailable.";
    static final String ENROLLMENT_UNAVAILABLE = "Course enrollment not found or unavailable.";
    static final String NO_RESULT = "No result has been saved for this student yet. Save it before publishing.";
    static final String WITHDRAWN = "A new result cannot be recorded for a withdrawn enrollment.";
    static final String MARKS_REQUIRED = "Marks obtained and maximum marks are required.";
    static final String NEGATIVE_MARKS = "Marks obtained cannot be negative.";
    static final String MAX_NOT_POSITIVE = "Maximum marks must be greater than zero.";
    static final String MARKS_OVER_MAX = "Marks obtained cannot exceed maximum marks.";
    static final String MARKS_TOO_PRECISE = "Marks must be below 1,000,000 with at most 2 decimal places.";
    static final String GRADE_TOO_LONG = "Grade must be at most 20 characters.";
    static final String REMARKS_TOO_LONG = "Remarks must be at most 2,000 characters.";

    private final CourseResultRepository results;
    private final CourseEnrollmentRepository courseEnrollments;
    private final FacultyTeachingAccessService teachingAccess;
    private final Clock clock;

    public CourseResultManagementService(
            CourseResultRepository results,
            CourseEnrollmentRepository courseEnrollments,
            FacultyTeachingAccessService teachingAccess,
            Clock clock) {
        this.results = results;
        this.courseEnrollments = courseEnrollments;
        this.teachingAccess = teachingAccess;
        this.clock = clock;
    }

    /**
     * Creates the enrollment's draft result, edits its draft, or corrects its published result, which stays
     * published with its first publication time. A new result needs an enrolled or completed enrollment; an existing
     * one is kept and can still be corrected whatever happened to the enrollment since. The acting faculty profile
     * becomes the one that recorded it.
     *
     * <p>The enrollment row is locked first and the result is read with a lock, so two saves of the same enrollment
     * take turns and its result is created once; a unique constraint backs this up.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public CourseResult save(Long userAccountId, Long courseOfferingId, Long courseEnrollmentId, BigDecimal marksObtained,
            BigDecimal maxMarks, String gradeCode, String remarks) {
        TeachingAssignment teaching = teachingAccess.findCurrentTeachingAssignment(userAccountId, courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        CourseEnrollment enrollment = enrollmentOf(courseOfferingId, courseEnrollmentId, true);
        requireValid(marksObtained, maxMarks, gradeCode, remarks);

        Optional<CourseResult> existing = results.findForUpdateByCourseEnrollmentId(enrollment.getId());
        if (existing.isPresent()) {
            CourseResult result = existing.get();
            result.record(teaching.getFacultyProfile(), marksObtained, maxMarks, gradeCode, remarks);
            return results.saveAndFlush(result);
        }
        if (enrollment.getStatus() == CourseEnrollmentStatus.WITHDRAWN) {
            throw new ApiException(HttpStatus.CONFLICT, WITHDRAWN);
        }
        return results.saveAndFlush(
                new CourseResult(enrollment, teaching.getFacultyProfile(), marksObtained, maxMarks, gradeCode, remarks));
    }

    /**
     * Publishes the enrollment's saved result, so the student sees it. Publishing a published result changes nothing
     * and keeps its first publication time. Nothing is created when there is no result yet.
     */
    public CourseResult publish(Long userAccountId, Long courseOfferingId, Long courseEnrollmentId) {
        if (!teachingAccess.canTeachCourseOffering(userAccountId, courseOfferingId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        CourseEnrollment enrollment = enrollmentOf(courseOfferingId, courseEnrollmentId, false);
        CourseResult result = results.findForUpdateByCourseEnrollmentId(enrollment.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NO_RESULT));
        if (result.isPublished()) {
            return result;
        }
        result.publish(clock.instant());
        return results.saveAndFlush(result);
    }

    // The enrollment must exist and belong to the offering in the route, which the caller teaches.
    private CourseEnrollment enrollmentOf(Long courseOfferingId, Long courseEnrollmentId, boolean lock) {
        Optional<CourseEnrollment> enrollment = courseEnrollmentId == null
                ? Optional.empty()
                : lock ? courseEnrollments.findByIdForUpdate(courseEnrollmentId) : courseEnrollments.findById(courseEnrollmentId);
        return enrollment
                .filter(found -> courseOfferingId.equals(found.getCourseOffering().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ENROLLMENT_UNAVAILABLE));
    }

    // Clear messages for the rules the entity enforces, checked before anything is changed.
    private static void requireValid(BigDecimal marksObtained, BigDecimal maxMarks, String gradeCode, String remarks) {
        if (marksObtained == null || maxMarks == null) {
            throw badRequest(MARKS_REQUIRED);
        }
        if (marksObtained.signum() < 0) {
            throw badRequest(NEGATIVE_MARKS);
        }
        if (maxMarks.signum() <= 0) {
            throw badRequest(MAX_NOT_POSITIVE);
        }
        for (BigDecimal marks : new BigDecimal[] {marksObtained, maxMarks}) {
            if (marks.stripTrailingZeros().scale() > CourseResult.MARK_DECIMAL_PLACES
                    || marks.compareTo(CourseResult.MAX_MARKS_LIMIT) > 0) {
                throw badRequest(MARKS_TOO_PRECISE);
            }
        }
        if (marksObtained.compareTo(maxMarks) > 0) {
            throw badRequest(MARKS_OVER_MAX);
        }
        try {
            CourseResult.gradeCodeOrNull(gradeCode);
        } catch (IllegalArgumentException tooLong) {
            throw badRequest(GRADE_TOO_LONG);
        }
        if (remarks != null && remarks.strip().length() > CourseResult.MAX_REMARKS_LENGTH) {
            throw badRequest(REMARKS_TOO_LONG);
        }
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
