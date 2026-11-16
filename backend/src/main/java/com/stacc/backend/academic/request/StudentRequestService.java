package com.stacc.backend.academic.request;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.result.AcademicStandingService;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.result.GradeScale;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.notification.NotificationMessage;
import com.stacc.backend.notification.NotificationService;
import com.stacc.backend.notification.NotificationSourceType;
import com.stacc.backend.notification.NotificationType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student requests to the ERP office (v0.16.5): medical leave, the correction of an absence, and the revaluation of a
 * result. Students raise and cancel only their own, found from the account in the token; the office (`ADMIN` with
 * `ERP_REQUEST_MANAGE`) decides each once under the request's row lock. An approved correction marks the record present;
 * an approved revaluation with new marks applies them to the result, which stays published.
 */
@Service
@Transactional
public class StudentRequestService {

    static final String NOT_FOUND = "Request not found.";
    static final String RECORD_NOT_FOUND = "Attendance record not found.";
    static final String RESULT_NOT_FOUND = "Result not found.";
    static final int MAX_LEAVE_DAYS = 30;

    public record RequestView(Long requestId, String reference, StudentRequest.Kind kind, StudentRequest.Status status,
            String requesterLoginId, String courseCode, String courseName, Instant classHeldAt, LocalDate fromDate,
            LocalDate toDate, BigDecimal marksObtained, BigDecimal maxMarks, String reason, String outcome,
            String decisionComment, Instant createdAt, Instant decidedAt) {
    }

    private final StudentRequestRepository requests;
    private final UserAccountRepository accounts;
    private final StudentProfileRepository students;
    private final AttendanceRecordRepository records;
    private final CourseResultRepository results;
    private final AcademicStandingService standing;
    private final NotificationService notifications;
    private final Clock clock;
    private final ZoneId collegeZone;

    public StudentRequestService(StudentRequestRepository requests, UserAccountRepository accounts, StudentProfileRepository students,
            AttendanceRecordRepository records, CourseResultRepository results, AcademicStandingService standing,
            NotificationService notifications, Clock clock, com.stacc.backend.engagement.EngagementProperties college) {
        this.requests = requests;
        this.accounts = accounts;
        this.students = students;
        this.records = records;
        this.results = results;
        this.standing = standing;
        this.notifications = notifications;
        this.clock = clock;
        this.collegeZone = college.zone();
    }

    // --- Students

    @Transactional(readOnly = true)
    public List<RequestView> mine(Long accountId) {
        return requests.findAllByRequester(accountId).stream().map(StudentRequestService::view).toList();
    }

    public RequestView medicalLeave(Long accountId, LocalDate from, LocalDate to, String reason) {
        UserAccount student = activeStudent(accountId);
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the first and the last day of the leave, the last not before the first.");
        }
        LocalDate today = LocalDate.now(clock.withZone(collegeZone));
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_LEAVE_DAYS || from.isBefore(today.minusDays(60)) || to.isAfter(today.plusDays(60))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Leave covers at most 30 days, within 60 days of today.");
        }
        return view(requests.saveAndFlush(StudentRequest.medicalLeave(student, from, to, reasonOf(reason), clock.instant())));
    }

    /** Asks for a finalized absence of one's own to be corrected. Anyone else's record is the same 404 as none. */
    public RequestView correction(Long accountId, Long attendanceRecordId, String reason) {
        UserAccount student = activeStudent(accountId);
        AttendanceRecord record = (attendanceRecordId == null ? Optional.<AttendanceRecord>empty() : records.findById(attendanceRecordId))
                .filter(found -> found.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getId().equals(accountId))
                .filter(found -> found.getAttendanceSession().isFinalized())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RECORD_NOT_FOUND));
        if (record.getStatus() != AttendanceStatus.ABSENT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a class marked absent can be corrected.");
        }
        return saveOnce(StudentRequest.correction(student, record, reasonOf(reason), clock.instant()));
    }

    /** Asks for a published result of one's own to be checked again. */
    public RequestView revaluation(Long accountId, Long courseResultId, String reason) {
        UserAccount student = activeStudent(accountId);
        CourseResult result = (courseResultId == null ? Optional.<CourseResult>empty() : results.findById(courseResultId))
                .filter(CourseResult::isPublished)
                .filter(found -> found.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getId().equals(accountId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RESULT_NOT_FOUND));
        return saveOnce(StudentRequest.revaluation(student, result, reasonOf(reason), clock.instant()));
    }

    public RequestView cancel(Long accountId, Long requestId) {
        StudentRequest request = lockedRequest(requestId);
        if (!request.getRequester().getId().equals(accountId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND);
        }
        if (request.getStatus() != StudentRequest.Status.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "Only a request that has not been decided can be cancelled.");
        }
        request.cancel(clock.instant());
        return view(requests.saveAndFlush(request));
    }

    // --- The ERP office

    @Transactional(readOnly = true)
    public List<RequestView> queue(StudentRequest.Kind kind, StudentRequest.Status status, String loginId) {
        String text = loginId == null ? "" : loginId.strip().toLowerCase(Locale.ROOT);
        if (text.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Search with at most 100 characters.");
        }
        String like = text.isEmpty() ? null : "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return requests.queue(kind, status, like, PageRequest.of(0, 200)).stream().map(StudentRequestService::view).toList();
    }

    /**
     * Approves a request. A correction marks the absence present; a revaluation with new marks applies them (on the
     * external part when the result is split), and without new marks records that nothing changed.
     */
    public RequestView approve(Long officeAccountId, Long requestId, String comment, BigDecimal newMarks) {
        StudentRequest request = lockedSubmitted(requestId);
        Instant now = clock.instant();
        String outcome = switch (request.getKind()) {
            case MEDICAL_LEAVE -> "Leave approved";
            case ATTENDANCE_CORRECTION -> markPresent(request, now);
            case REVALUATION -> revalue(request, newMarks);
        };
        request.decide(true, accounts.getReferenceById(officeAccountId), commentOf(comment, false), outcome, now);
        StudentRequest saved = requests.saveAndFlush(request);
        notifyDecision(saved);
        return view(saved);
    }

    /** Rejects a request, always with a comment the student can read. */
    public RequestView reject(Long officeAccountId, Long requestId, String comment) {
        StudentRequest request = lockedSubmitted(requestId);
        request.decide(false, accounts.getReferenceById(officeAccountId), commentOf(comment, true), null, clock.instant());
        StudentRequest saved = requests.saveAndFlush(request);
        notifyDecision(saved);
        return view(saved);
    }

    private String markPresent(StudentRequest request, Instant now) {
        AttendanceRecord record = records.findByIdForUpdate(request.getAttendanceRecord().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RECORD_NOT_FOUND));
        if (record.getStatus() == AttendanceStatus.PRESENT) {
            return "Already present";
        }
        record.mark(AttendanceStatus.PRESENT, record.getMarkedByFacultyProfile(), now);
        records.saveAndFlush(record);
        return "Marked present";
    }

    private String revalue(StudentRequest request, BigDecimal newMarks) {
        CourseResult result = results.findForUpdateByCourseEnrollmentId(request.getCourseResult().getCourseEnrollment().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RESULT_NOT_FOUND));
        BigDecimal old = result.getMarksObtained();
        if (newMarks == null || newMarks.compareTo(old) == 0) {
            return "No change";
        }
        if (newMarks.signum() < 0 || newMarks.compareTo(result.getMaxMarks()) > 0 || newMarks.stripTrailingZeros().scale() > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New marks are between 0 and the maximum, with up to 2 decimals.");
        }
        if (result.getInternalMarks() != null) {
            BigDecimal external = newMarks.subtract(result.getInternalMarks());
            if (external.signum() < 0 || external.compareTo(result.getExternalMax()) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "The new marks must keep the external part between 0 and its maximum.");
            }
            result.recordSplit(result.getInternalMarks(), result.getInternalMax(), external, result.getExternalMax());
        }
        // An entered grade on the scale is re-graded from the new marks; with none, the scale keeps deciding.
        String grade = result.getGradeCode();
        GradeScale scale = standing.scale();
        if (grade != null && scale.gradeOf(grade, old, result.getMaxMarks()).isPresent()) {
            grade = scale.forPercent(GradeScale.percent(newMarks, result.getMaxMarks())).map(GradeScale.Grade::code).orElse(grade);
        }
        result.record(result.getRecordedByFacultyProfile(), newMarks, result.getMaxMarks(), grade, result.getRemarks());
        results.saveAndFlush(result);
        return "Marks changed " + old.stripTrailingZeros().toPlainString() + " → " + newMarks.stripTrailingZeros().toPlainString();
    }

    private void notifyDecision(StudentRequest request) {
        String what = switch (request.getKind()) {
            case MEDICAL_LEAVE -> "medical leave";
            case ATTENDANCE_CORRECTION -> "attendance correction";
            case REVALUATION -> "revaluation";
        };
        String verdict = request.getStatus() == StudentRequest.Status.APPROVED ? "approved" : "not approved";
        notifications.notifyUser(new NotificationMessage(NotificationType.ERP_REQUEST_DECIDED,
                "Request " + request.reference() + " " + verdict,
                "Your " + what + " request was " + verdict + (request.getOutcome() == null ? "." : ": " + request.getOutcome() + "."),
                NotificationSourceType.STUDENT_REQUEST, request.getId(),
                request.getKind() == StudentRequest.Kind.REVALUATION ? "/erp/exams" : "/erp/attendance"),
                request.getRequester().getId());
    }

    private RequestView saveOnce(StudentRequest request) {
        try {
            return view(requests.saveAndFlush(request));
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, "You already have an open request for this.");
        }
    }

    private StudentRequest lockedRequest(Long requestId) {
        return (requestId == null ? Optional.<StudentRequest>empty() : requests.findByIdForUpdate(requestId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
    }

    private StudentRequest lockedSubmitted(Long requestId) {
        StudentRequest request = lockedRequest(requestId);
        if (request.getStatus() != StudentRequest.Status.SUBMITTED) {
            throw new ApiException(HttpStatus.CONFLICT, "This request has already been decided or cancelled.");
        }
        return request;
    }

    private UserAccount activeStudent(Long accountId) {
        return students.findWithContextByUserAccountId(accountId)
                .filter(profile -> profile.getStatus() == StudentProfileStatus.ACTIVE)
                .map(profile -> profile.getUserAccount())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Requests need an active student profile."));
    }

    private static String reasonOf(String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.length() < 3 || text.length() > StudentRequest.MAX_REASON) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Give a reason of 3 to 1,000 characters.");
        }
        return text;
    }

    private static String commentOf(String comment, boolean required) {
        String text = comment == null ? "" : comment.strip();
        if (required && text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Say why the request is rejected.");
        }
        if (text.length() > StudentRequest.MAX_COMMENT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A comment is at most 1,000 characters.");
        }
        return text.isEmpty() ? null : text;
    }

    static RequestView view(StudentRequest request) {
        Course course = null;
        Instant heldAt = null;
        BigDecimal marks = null;
        BigDecimal max = null;
        if (request.getAttendanceRecord() != null) {
            course = request.getAttendanceRecord().getCourseEnrollment().getCourseOffering().getCourse();
            heldAt = request.getAttendanceRecord().getAttendanceSession().getHeldAt();
        }
        if (request.getCourseResult() != null) {
            course = request.getCourseResult().getCourseEnrollment().getCourseOffering().getCourse();
            marks = request.getCourseResult().getMarksObtained();
            max = request.getCourseResult().getMaxMarks();
        }
        return new RequestView(request.getId(), request.reference(), request.getKind(), request.getStatus(),
                request.getRequester().getLoginId(), course == null ? null : course.getCode(), course == null ? null : course.getName(),
                heldAt, request.getFromDate(), request.getToDate(), marks, max, request.getReason(), request.getOutcome(),
                request.getDecisionComment(), request.getCreatedAt(), request.getDecidedAt());
    }
}
