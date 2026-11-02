package com.stacc.backend.academic.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.identity.student.StudentProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin enrollment monitor: every official course enrollment, searched and filtered on the server and read one
 * page at a time. It only reads; changes stay on the student's enrollment page.
 */
@Service
@Transactional(readOnly = true)
public class EnrollmentMonitorService {

    public static final int MAX_PAGE_SIZE = 100;
    static final String QUERY_TOO_LONG = "Search text must be at most 100 characters.";
    static final String BAD_PAGE = "Page must be 0 or more and size from 1 to 100.";

    private final CourseEnrollmentRepository courseEnrollments;
    private final OfferingFacultyLoader people;

    public EnrollmentMonitorService(CourseEnrollmentRepository courseEnrollments, OfferingFacultyLoader people) {
        this.courseEnrollments = courseEnrollments;
        this.people = people;
    }

    /** Filters a null field leaves out. */
    public record Filters(Long academicSessionId, Long programId, Long semesterId, Long sectionId, Long courseId,
            CourseEnrollmentStatus status, String query) {
    }

    public record MonitorRow(
            @Schema(example = "501") Long courseEnrollmentId,
            @Schema(example = "57") Long studentProfileId,
            @Schema(description = "The student ID, which is the account's login ID.", example = "student1") String loginId,
            @Schema(description = "The name from the person profile, or null.", example = "Aarav Sharma") String name,
            @Schema(example = "25CSE001") String rollNumber,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4") BigDecimal credits,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(description = "The student's section in the semester, or null.", example = "A") String sectionCode,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN", "DROPPED"}) String status,
            @Schema(description = "When the enrollment was made.") Instant registeredAt,
            @Schema(description = "When the student dropped it, or null.") Instant droppedAt) {
    }

    public record MonitorPage(
            List<MonitorRow> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "37") long totalElements,
            @Schema(example = "2") int totalPages) {
    }

    public MonitorPage search(Filters filters, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw AcademicMasterErrors.badRequest(BAD_PAGE);
        }
        String text = filters.query() == null ? "" : filters.query().strip();
        if (text.length() > 100) {
            throw AcademicMasterErrors.badRequest(QUERY_TOO_LONG);
        }
        String pattern = text.isEmpty()
                ? null : "%" + AdminEnrollmentQueryService.escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
        Page<CourseEnrollment> found = courseEnrollments.searchForMonitor(filters.academicSessionId(),
                filters.programId(), filters.semesterId(), filters.sectionId(), filters.courseId(), filters.status(),
                pattern, PageRequest.of(page, size));
        Map<Long, String> names = people.namesOfAccounts(found.getContent().stream()
                .map(enrollment -> enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount().getId())
                .distinct()
                .toList());
        List<MonitorRow> rows = found.getContent().stream().map(enrollment -> row(enrollment, names)).toList();
        return new MonitorPage(rows, found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }

    private static MonitorRow row(CourseEnrollment enrollment, Map<Long, String> names) {
        SemesterEnrollment term = enrollment.getSemesterEnrollment();
        StudentProfile student = term.getStudentProfile();
        CourseOffering offering = enrollment.getCourseOffering();
        Semester semester = offering.getSemester();
        return new MonitorRow(enrollment.getId(), student.getId(), student.getUserAccount().getLoginId(),
                names.get(student.getUserAccount().getId()), student.getRollNumber(), offering.getCourse().getCode(),
                offering.getCourse().getName(), offering.getCourse().getCredits(),
                semester.getAcademicSession().getCode(), semester.getProgram().getCode(), semester.getSemesterNumber(),
                term.getSection() == null ? null : term.getSection().getCode(), enrollment.getStatus().name(),
                enrollment.getCreatedAt(), enrollment.getDroppedAt());
    }
}
