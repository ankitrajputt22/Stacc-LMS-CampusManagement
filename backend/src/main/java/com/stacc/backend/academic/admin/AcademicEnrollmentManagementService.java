package com.stacc.backend.academic.admin;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentSectionChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.SemesterEnrollmentStatusChange;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseEnrollmentResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.SemesterEnrollmentResponse;
import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.section.SectionRepository;
import com.stacc.backend.academic.section.SectionStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The official enrollment records, one at a time. A student is enrolled in a semester of their own program, and
 * through that semester enrollment in course offerings of the same semester. New enrollments start {@code ENROLLED};
 * an enrolled one may become {@code COMPLETED} or {@code WITHDRAWN}, and an ended one never changes again. A semester
 * enrollment ends only after each of its course enrollments has been ended on its own: nothing cascades, and nothing
 * is ever deleted. Accounts and profiles are never created or changed here.
 */
@Service
@Transactional
public class AcademicEnrollmentManagementService {

    static final String STUDENT_NOT_FOUND = AdminEnrollmentQueryService.STUDENT_NOT_FOUND;
    static final String SEMESTER_MISSING = CourseOfferingManagementService.SEMESTER_MISSING;
    static final String STUDENT_INACTIVE = "This student profile is inactive and cannot be enrolled.";
    static final String OTHER_PROGRAM = "This semester belongs to a different program than the student's.";
    static final String SEMESTER_TAKEN = "This student is already enrolled in this semester.";
    static final String SEMESTER_ENROLLMENT_NOT_FOUND = AdminEnrollmentQueryService.SEMESTER_ENROLLMENT_NOT_FOUND;
    static final String SEMESTER_ENDED = "A completed or withdrawn semester enrollment cannot be changed.";
    static final String ACTIVE_COURSES =
            "Active course enrollments must be resolved before closing the semester enrollment.";
    static final String OFFERING_MISSING = "The selected course offering does not exist.";
    static final String NOT_ENROLLED_IN_SEMESTER =
            "Courses can be added only while the semester enrollment is enrolled.";
    static final String OTHER_SEMESTER =
            "This course offering belongs to a different semester than the semester enrollment.";
    static final String COURSE_TAKEN = "This student is already enrolled in this course offering.";
    static final String COURSE_ENROLLMENT_NOT_FOUND = "Course enrollment not found.";
    static final String COURSE_ENDED = "A completed or withdrawn course enrollment cannot be changed.";
    static final String SECTION_MISSING = "The selected section does not exist.";
    static final String SECTION_OTHER_SEMESTER = "This section belongs to a different semester.";
    static final String SECTION_INACTIVE = "This section is inactive, so no students can be placed in it.";
    static final String SECTION_COURSES =
            "This student is still enrolled in course offerings of their current section. Withdraw or complete them first.";

    private final StudentProfileRepository students;
    private final SemesterRepository semesters;
    private final CourseOfferingRepository offerings;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final CourseEnrollmentRepository courseEnrollments;
    private final SectionRepository sections;

    public AcademicEnrollmentManagementService(StudentProfileRepository students, SemesterRepository semesters,
            CourseOfferingRepository offerings, SemesterEnrollmentRepository semesterEnrollments,
            CourseEnrollmentRepository courseEnrollments, SectionRepository sections) {
        this.students = students;
        this.semesters = semesters;
        this.offerings = offerings;
        this.semesterEnrollments = semesterEnrollments;
        this.courseEnrollments = courseEnrollments;
        this.sections = sections;
    }

    public SemesterEnrollmentResponse createSemesterEnrollment(Long studentProfileId, SemesterEnrollmentCreate request) {
        StudentProfile student = students.findWithContextById(studentProfileId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(STUDENT_NOT_FOUND));
        Semester semester = semesters.findWithContextById(request.semesterId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_MISSING));
        if (student.getStatus() != StudentProfileStatus.ACTIVE) {
            throw AcademicMasterErrors.conflict(STUDENT_INACTIVE);
        }
        if (!semester.getProgram().getId().equals(student.getProgram().getId())) {
            throw AcademicMasterErrors.conflict(OTHER_PROGRAM);
        }
        if (semesterEnrollments.findByStudentProfileIdAndSemesterId(student.getId(), semester.getId()).isPresent()) {
            throw AcademicMasterErrors.conflict(SEMESTER_TAKEN);
        }
        return SemesterEnrollmentResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> semesterEnrollments.saveAndFlush(new SemesterEnrollment(student, semester)), SEMESTER_TAKEN));
    }

    /**
     * Ends an enrolled semester enrollment, or does nothing when it already has the status. The row stays locked
     * until the change is saved, and adding a course takes the same lock, so no course is added while it ends.
     */
    public SemesterEnrollmentResponse changeSemesterEnrollmentStatus(Long semesterEnrollmentId,
            SemesterEnrollmentStatusChange request) {
        SemesterEnrollment enrollment = semesterEnrollments.findByIdForUpdate(semesterEnrollmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_ENROLLMENT_NOT_FOUND));
        SemesterEnrollmentStatus target = request.status();
        if (target == enrollment.getStatus()) {
            return SemesterEnrollmentResponse.of(enrollment);
        }
        if (enrollment.getStatus() != SemesterEnrollmentStatus.ENROLLED) {
            throw AcademicMasterErrors.conflict(SEMESTER_ENDED);
        }
        if (courseEnrollments.existsBySemesterEnrollmentIdAndStatus(enrollment.getId(), CourseEnrollmentStatus.ENROLLED)) {
            throw AcademicMasterErrors.conflict(ACTIVE_COURSES);
        }
        if (target == SemesterEnrollmentStatus.COMPLETED) {
            enrollment.markCompleted();
        } else {
            enrollment.withdraw();
        }
        return SemesterEnrollmentResponse.of(semesterEnrollments.saveAndFlush(enrollment));
    }

    /**
     * Places an enrolled student in a section of their semester, moves them to another, or takes them out with no
     * section. The semester enrollment is locked first and then the section, always in that order, and the section's
     * enrolled students are counted only under its lock, so two placements can never take its last place twice.
     * Placing a student in the section they are already in changes nothing.
     */
    public SemesterEnrollmentResponse changeSection(Long semesterEnrollmentId, SemesterEnrollmentSectionChange request) {
        SemesterEnrollment enrollment = semesterEnrollments.findByIdForUpdate(semesterEnrollmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_ENROLLMENT_NOT_FOUND));
        if (enrollment.getStatus() != SemesterEnrollmentStatus.ENROLLED) {
            throw AcademicMasterErrors.conflict(SEMESTER_ENDED);
        }
        Section current = enrollment.getSection();
        if (current != null && current.getId().equals(request.sectionId())) {
            return SemesterEnrollmentResponse.of(enrollment);
        }
        if (courseEnrollments.existsEnrolledInOtherSectionOffering(enrollment.getId(), request.sectionId())) {
            throw AcademicMasterErrors.conflict(SECTION_COURSES);
        }
        if (request.sectionId() == null) {
            enrollment.placeIn(null);
            return SemesterEnrollmentResponse.of(semesterEnrollments.saveAndFlush(enrollment));
        }
        Section section = sections.findByIdForUpdate(request.sectionId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(SECTION_MISSING));
        if (!section.getSemester().getId().equals(enrollment.getSemester().getId())) {
            throw AcademicMasterErrors.conflict(SECTION_OTHER_SEMESTER);
        }
        if (section.getStatus() != SectionStatus.ACTIVE) {
            throw AcademicMasterErrors.conflict(SECTION_INACTIVE);
        }
        Integer capacity = section.getCapacity();
        if (capacity != null && semesterEnrollments.countEnrolledInSectionForShare(section.getId()) >= capacity) {
            throw AcademicMasterErrors.conflict("Section " + section.getCode() + " is full: "
                    + (capacity == 1 ? "its one place is taken." : "all " + capacity + " places are taken."));
        }
        enrollment.placeIn(section);
        return SemesterEnrollmentResponse.of(semesterEnrollments.saveAndFlush(enrollment));
    }

    /** Enrolls the semester enrollment's student in one offering of the same semester. */
    public CourseEnrollmentResponse createCourseEnrollment(Long semesterEnrollmentId, CourseEnrollmentCreate request) {
        SemesterEnrollment semesterEnrollment = semesterEnrollments.findByIdForUpdate(semesterEnrollmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_ENROLLMENT_NOT_FOUND));
        CourseOffering offering = offerings.findWithContextById(request.courseOfferingId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(OFFERING_MISSING));
        if (semesterEnrollment.getStatus() != SemesterEnrollmentStatus.ENROLLED) {
            throw AcademicMasterErrors.conflict(NOT_ENROLLED_IN_SEMESTER);
        }
        if (!offering.getSemester().getId().equals(semesterEnrollment.getSemester().getId())) {
            throw AcademicMasterErrors.conflict(OTHER_SEMESTER);
        }
        Section offeringSection = offering.getSection();
        if (offeringSection != null && (semesterEnrollment.getSection() == null
                || !semesterEnrollment.getSection().getId().equals(offeringSection.getId()))) {
            throw AcademicMasterErrors.conflict("This course offering is for section " + offeringSection.getCode()
                    + ". Place the student in section " + offeringSection.getCode() + " first.");
        }
        if (courseEnrollments.findBySemesterEnrollmentIdAndCourseOfferingId(semesterEnrollment.getId(), offering.getId())
                .isPresent()) {
            throw AcademicMasterErrors.conflict(COURSE_TAKEN);
        }
        return CourseEnrollmentResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> courseEnrollments.saveAndFlush(new CourseEnrollment(semesterEnrollment, offering)), COURSE_TAKEN));
    }

    /**
     * Ends an enrolled course enrollment, or does nothing when it already has the status. An enrollment never
     * returns to {@code ENROLLED}, so it can never be enrolled under a semester enrollment that has ended.
     */
    public CourseEnrollmentResponse changeCourseEnrollmentStatus(Long courseEnrollmentId,
            CourseEnrollmentStatusChange request) {
        CourseEnrollment enrollment = courseEnrollments.findByIdForUpdate(courseEnrollmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(COURSE_ENROLLMENT_NOT_FOUND));
        CourseEnrollmentStatus target = request.status();
        if (target == enrollment.getStatus()) {
            return CourseEnrollmentResponse.of(enrollment);
        }
        if (enrollment.getStatus() != CourseEnrollmentStatus.ENROLLED) {
            throw AcademicMasterErrors.conflict(COURSE_ENDED);
        }
        if (target == CourseEnrollmentStatus.COMPLETED) {
            enrollment.markCompleted();
        } else {
            enrollment.withdraw();
        }
        return CourseEnrollmentResponse.of(courseEnrollments.saveAndFlush(enrollment));
    }

    /** The course enrollment, which must still be {@code ENROLLED} to be given LMS access. */
    @Transactional(readOnly = true)
    public CourseEnrollmentResponse requireEnrolledCourseEnrollment(Long courseEnrollmentId) {
        CourseEnrollment enrollment = courseEnrollments.findWithOfferingById(courseEnrollmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(COURSE_ENROLLMENT_NOT_FOUND));
        if (enrollment.getStatus() != CourseEnrollmentStatus.ENROLLED) {
            throw AcademicMasterErrors.conflict(CourseEnrollmentWorkflow.NOT_ENROLLED);
        }
        return CourseEnrollmentResponse.of(enrollment);
    }
}
