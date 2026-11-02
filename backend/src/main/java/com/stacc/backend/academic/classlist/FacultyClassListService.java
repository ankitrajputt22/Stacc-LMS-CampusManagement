package com.stacc.backend.academic.classlist;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.academic.admin.OfferingFacultyLoader;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository.OfferingStatusCount;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Faculty class lists: the students registered in the course offerings a faculty member teaches now, read-only. Access
 * comes only from an active teaching assignment ({@link FacultyTeachingAccessService}); an offering the caller does not
 * teach answers the same 404 as a missing one. Faculty never change enrollments here.
 */
@Service
@Transactional(readOnly = true)
public class FacultyClassListService {

    static final String UNAVAILABLE = "Course offering not found or unavailable.";

    private final TeachingAssignmentRepository teachingAssignments;
    private final FacultyTeachingAccessService teachingAccess;
    private final CourseEnrollmentRepository courseEnrollments;
    private final OfferingFacultyLoader people;

    public FacultyClassListService(TeachingAssignmentRepository teachingAssignments,
            FacultyTeachingAccessService teachingAccess, CourseEnrollmentRepository courseEnrollments,
            OfferingFacultyLoader people) {
        this.teachingAssignments = teachingAssignments;
        this.teachingAccess = teachingAccess;
        this.courseEnrollments = courseEnrollments;
        this.people = people;
    }

    public record TaughtOffering(
            @Schema(example = "101") Long courseOfferingId,
            @Schema(example = "CS301") String courseCode,
            @Schema(example = "Data Structures") String courseName,
            @Schema(example = "4") BigDecimal credits,
            @Schema(example = "2026-27") String academicSessionCode,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(description = "The section it is offered to, or null for the whole semester.") String sectionCode,
            @Schema(description = "Places in total, or null for no limit.") Integer capacity,
            @Schema(description = "Students enrolled now.", example = "52") long enrolledCount) {
    }

    public record ClassStudent(
            @Schema(example = "501") Long courseEnrollmentId,
            @Schema(description = "The student ID, which is the account's login ID.", example = "student1") String loginId,
            @Schema(description = "The name from the person profile, or null.") String name,
            @Schema(example = "25CSE001") String rollNumber,
            @Schema(example = "BTECH-CSE") String programCode,
            @Schema(example = "3") int semesterNumber,
            @Schema(description = "The student's section, or null.") String sectionCode,
            @Schema(allowableValues = {"ENROLLED", "COMPLETED", "WITHDRAWN", "DROPPED"}) String status,
            Instant registeredAt) {
    }

    public record ClassList(TaughtOffering offering, List<ClassStudent> students) {
    }

    /** The offerings the caller teaches now, latest session first, then by course code. */
    public List<TaughtOffering> myOfferings(Long userAccountId) {
        List<CourseOffering> taught = teachingAssignments.findAllWithCourseDetailsByUserAccountId(userAccountId).stream()
                .filter(assignment -> FacultyTeachingAccessService.isCurrentTeaching(assignment, userAccountId))
                .map(TeachingAssignment::getCourseOffering)
                .distinct()
                .toList();
        Map<Long, Long> enrolled = enrolledCounts(taught.stream().map(CourseOffering::getId).toList());
        return taught.stream()
                .sorted(Comparator.comparing((CourseOffering offering) -> offering.getSemester().getAcademicSession()
                                .getStartDate()).reversed()
                        .thenComparing(offering -> offering.getCourse().getCode())
                        .thenComparing(CourseOffering::getId))
                .map(offering -> taughtOffering(offering, enrolled.getOrDefault(offering.getId(), 0L)))
                .toList();
    }

    /** Every enrollment of an offering the caller teaches now, whatever its status, by student ID. */
    public ClassList classList(Long userAccountId, Long courseOfferingId) {
        TeachingAssignment teaching = teachingAccess.findCurrentTeachingAssignment(userAccountId, courseOfferingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, UNAVAILABLE));
        CourseOffering offering = teaching.getCourseOffering();
        List<CourseEnrollment> enrollments = courseEnrollments.findAllWithStudentByCourseOfferingId(courseOfferingId);
        Map<Long, String> names = people.namesOfAccounts(enrollments.stream()
                .map(enrollment -> enrollment.getSemesterEnrollment().getStudentProfile().getUserAccount().getId())
                .distinct()
                .toList());
        long enrolled = enrollments.stream()
                .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED).count();
        List<ClassStudent> students = enrollments.stream().map(enrollment -> {
            StudentProfile student = enrollment.getSemesterEnrollment().getStudentProfile();
            var section = enrollment.getSemesterEnrollment().getSection();
            return new ClassStudent(enrollment.getId(), student.getUserAccount().getLoginId(),
                    names.get(student.getUserAccount().getId()), student.getRollNumber(), student.getProgram().getCode(),
                    offering.getSemester().getSemesterNumber(), section == null ? null : section.getCode(),
                    enrollment.getStatus().name(), enrollment.getCreatedAt());
        }).toList();
        return new ClassList(taughtOffering(offering, enrolled), students);
    }

    private Map<Long, Long> enrolledCounts(List<Long> offeringIds) {
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        return courseEnrollments.countByOfferingAndStatus(offeringIds).stream()
                .filter(count -> count.getStatus() == CourseEnrollmentStatus.ENROLLED)
                .collect(Collectors.toMap(OfferingStatusCount::getCourseOfferingId, OfferingStatusCount::getTotal));
    }

    private static TaughtOffering taughtOffering(CourseOffering offering, long enrolled) {
        Semester semester = offering.getSemester();
        return new TaughtOffering(offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                offering.getCourse().getCredits(), semester.getAcademicSession().getCode(),
                semester.getProgram().getCode(), semester.getSemesterNumber(),
                offering.getSection() == null ? null : offering.getSection().getCode(), offering.getCapacity(),
                enrolled);
    }
}
