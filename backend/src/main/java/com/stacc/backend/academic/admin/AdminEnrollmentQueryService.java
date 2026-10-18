package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseOfferingResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingRosterResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.RosterEntryResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterCourseEnrollmentsResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.StudentLookupResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.StudentSemesterEnrollmentsResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What admins read for enrollment work: students found by login ID, a student's semester enrollments, a semester
 * enrollment's course enrollments, and an offering's roster. Everything is read-only. The student lookup is only for
 * choosing a student to enroll: it is not a user directory and shows no account data beyond the login ID.
 */
@Service
@Transactional(readOnly = true)
public class AdminEnrollmentQueryService {

    static final int SEARCH_LIMIT = 50;
    static final int MAX_QUERY_LENGTH = 100;
    static final String QUERY_TOO_LONG = "Search text must be at most 100 characters.";
    static final String STUDENT_NOT_FOUND = "Student not found.";
    static final String SEMESTER_ENROLLMENT_NOT_FOUND = "Semester enrollment not found.";

    private final StudentProfileRepository students;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final CourseEnrollmentRepository courseEnrollments;
    private final CourseOfferingRepository offerings;

    public AdminEnrollmentQueryService(StudentProfileRepository students,
            SemesterEnrollmentRepository semesterEnrollments, CourseEnrollmentRepository courseEnrollments,
            CourseOfferingRepository offerings) {
        this.students = students;
        this.semesterEnrollments = semesterEnrollments;
        this.courseEnrollments = courseEnrollments;
        this.offerings = offerings;
    }

    /**
     * Student profiles whose login ID contains the text, ignoring case, by login ID and at most {@value #SEARCH_LIMIT}.
     * Without text the first ones are listed. {@code %} and {@code _} match themselves, not any text.
     */
    public List<StudentLookupResponse> searchStudents(String query) {
        String text = query == null ? "" : query.strip();
        if (text.length() > MAX_QUERY_LENGTH) {
            throw AcademicMasterErrors.badRequest(QUERY_TOO_LONG);
        }
        return students.searchByLoginId("%" + escapeLike(text) + "%", PageRequest.of(0, SEARCH_LIMIT)).stream()
                .map(StudentLookupResponse::of)
                .toList();
    }

    public StudentSemesterEnrollmentsResponse studentSemesterEnrollments(Long studentProfileId) {
        StudentProfile student = students.findWithContextById(studentProfileId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(STUDENT_NOT_FOUND));
        List<SemesterEnrollmentResponse> enrollments = semesterEnrollments
                .findAllWithContextByStudentProfileId(student.getId()).stream()
                .map(SemesterEnrollmentResponse::of)
                .toList();
        return new StudentSemesterEnrollmentsResponse(StudentLookupResponse.of(student), enrollments);
    }

    public SemesterCourseEnrollmentsResponse semesterCourseEnrollments(Long semesterEnrollmentId) {
        SemesterEnrollment semesterEnrollment = semesterEnrollments.findWithContextById(semesterEnrollmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_ENROLLMENT_NOT_FOUND));
        List<CourseEnrollmentResponse> enrollments = courseEnrollments
                .findAllWithOfferingBySemesterEnrollmentId(semesterEnrollment.getId()).stream()
                .map(CourseEnrollmentResponse::of)
                .toList();
        return new SemesterCourseEnrollmentsResponse(StudentLookupResponse.of(semesterEnrollment.getStudentProfile()),
                SemesterEnrollmentResponse.of(semesterEnrollment), enrollments);
    }

    /** Every course enrollment of exactly this offering, whatever its status, by student login ID. */
    public OfferingRosterResponse roster(Long courseOfferingId) {
        CourseOffering offering = offerings.findWithContextById(courseOfferingId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(CourseOfferingManagementService.NOT_FOUND));
        List<RosterEntryResponse> enrollments = courseEnrollments.findAllWithStudentByCourseOfferingId(offering.getId())
                .stream()
                .map(RosterEntryResponse::of)
                .toList();
        return new OfferingRosterResponse(CourseOfferingResponse.of(offering), enrollments);
    }

    // The repository query uses ! as its escape character.
    static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
