package com.stacc.backend.academic.result;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CourseResultManagementService {

    static final String COURSE_UNAVAILABLE = "Course offering not found or unavailable.";
    static final String ENROLLMENT_UNAVAILABLE = "Course enrollment not found or unavailable.";
    static final String NO_RESULT = "No result has been saved for this student yet. Save it before publishing.";
    static final String WITHDRAWN = "A new result cannot be recorded for a withdrawn or dropped enrollment.";
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
    private final NotificationService notifications;

    public CourseResultManagementService(
            CourseResultRepository results,
            CourseEnrollmentRepository courseEnrollments,
            FacultyTeachingAccessService teachingAccess,
            Clock clock,
            NotificationService notifications) {
        this.results = results;
        this.courseEnrollments = courseEnrollments;
        this.teachingAccess = teachingAccess;
        this.clock = clock;
        this.notifications = notifications;
    }

    public CourseResult save(Long userAccountId, Long courseOfferingId, Long courseEnrollmentId, BigDecimal marksObtained,
            BigDecimal maxMarks, String gradeCode, String remarks) {
        return save(userAccountId, courseOfferingId, courseEnrollmentId, marksObtained, maxMarks, gradeCode, remarks, null, null, null, null);
    }

    static final String SPLIT_INCOMPLETE = "Give the internal and external marks with their maximums, or none of them.";
    static final String SPLIT_OVER = "A part's marks cannot exceed its maximum.";
    static final String SPLIT_SUM = "The internal and external marks must add up to the totals.";

    public CourseResult save(Long userAccountId, Long courseOfferingId, Long courseEnrollmentId, BigDecimal marksObtained,
            BigDecimal maxMarks, String gradeCode, String remarks, BigDecimal internalMarks, BigDecimal internalMax,
            BigDecimal externalMarks, BigDecimal externalMax) {
        long given = java.util.stream.Stream.of(internalMarks, internalMax, externalMarks, externalMax).filter(java.util.Objects::nonNull).count();
        if (given != 0 && given != 4) {
            throw badRequest(SPLIT_INCOMPLETE);
        }
        if (given == 4) {
            if (internalMarks.compareTo(internalMax) > 0 || externalMarks.compareTo(externalMax) > 0) {
                throw badRequest(SPLIT_OVER);
            }
            if (marksObtained == null || maxMarks == null || internalMarks.add(externalMarks).compareTo(marksObtained) != 0
                    || internalMax.add(externalMax).compareTo(maxMarks) != 0) {
                throw badRequest(SPLIT_SUM);
            }
        }
        CourseResult saved = saveTotals(userAccountId, courseOfferingId, courseEnrollmentId, marksObtained, maxMarks, gradeCode, remarks);
        saved.recordSplit(internalMarks, internalMax, externalMarks, externalMax);
        return results.saveAndFlush(saved);
    }

    private CourseResult saveTotals(Long userAccountId, Long courseOfferingId, Long courseEnrollmentId, BigDecimal marksObtained,
            BigDecimal maxMarks, String gradeCode, String remarks) {
        TeachingAssignment teaching = teachingAccess.findCurrentTeachingAssignment(userAccountId, courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        CourseEnrollment enrollment = enrollmentOf(courseOfferingId, courseEnrollmentId, true);
        requireValid(marksObtained, maxMarks, gradeCode, remarks);

        Optional<CourseResult> existing = results.findForUpdateByCourseEnrollmentId(enrollment.getId());
        if (existing.isPresent()) {
            CourseResult result = existing.get();
            boolean changed = result.getMarksObtained().compareTo(marksObtained) != 0
                    || result.getMaxMarks().compareTo(maxMarks) != 0
                    || !Objects.equals(result.getGradeCode(), CourseResult.gradeCodeOrNull(gradeCode))
                    || !Objects.equals(result.getRemarks(), CourseResult.remarksOrNull(remarks));
            result.record(teaching.getFacultyProfile(), marksObtained, maxMarks, gradeCode, remarks);
            CourseResult saved = results.saveAndFlush(result);
            if (saved.isPublished() && changed) {
                notify(saved, NotificationType.RESULT_UPDATED, "Result updated", "Your result for %s was updated.");
            }
            return saved;
        }
        if (enrollment.getStatus() != CourseEnrollmentStatus.ENROLLED
                && enrollment.getStatus() != CourseEnrollmentStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, WITHDRAWN);
        }
        return results.saveAndFlush(
                new CourseResult(enrollment, teaching.getFacultyProfile(), marksObtained, maxMarks, gradeCode, remarks));
    }

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
        CourseResult saved = results.saveAndFlush(result);
        notify(saved, NotificationType.RESULT_PUBLISHED, "Result published", "Your result for %s was published.");
        return saved;
    }

    private void notify(CourseResult result, NotificationType type, String title, String message) {
        CourseEnrollment enrollment = result.getCourseEnrollment();
        Course course = enrollment.getCourseOffering().getCourse();
        Long student = enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount().getId();
        notifications.notifyUser(new NotificationMessage(type, title,
                message.formatted(course.getCode() + " " + course.getName()), NotificationSourceType.COURSE_RESULT,
                result.getId(), "/academic/results"), student);
    }

    private CourseEnrollment enrollmentOf(Long courseOfferingId, Long courseEnrollmentId, boolean lock) {
        Optional<CourseEnrollment> enrollment = courseEnrollmentId == null
                ? Optional.empty()
                : lock ? courseEnrollments.findByIdForUpdate(courseEnrollmentId) : courseEnrollments.findById(courseEnrollmentId);
        return enrollment
                .filter(found -> courseOfferingId.equals(found.getCourseOffering().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ENROLLMENT_UNAVAILABLE));
    }

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
