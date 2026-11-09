package com.stacc.backend.academic.admin;

import java.util.List;
import java.util.Map;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseOfferingResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingFaculty;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseStatus;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.section.SectionRepository;
import com.stacc.backend.academic.section.SectionStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterStatus;
import com.stacc.backend.academic.semester.SemesterRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, and edit course offerings. An offering is one catalog course taught in one semester, both
 * loaded on the server from the IDs sent, at most once per pair. The course and semester say what the offering is:
 * enrollments, teaching assignments, attendance, results, and an LMS course hang off it, so they never change after
 * creation. Only the status can, moving forward from planned to active to closed. Nothing is ever deleted.
 */
@Service
@Transactional
public class CourseOfferingManagementService {

    static final String COURSE_INACTIVE = "This course is inactive, so it cannot be offered.";
    static final String SEMESTER_CLOSED = "This semester is closed, so it cannot take new course offerings.";
    static final String NOT_FOUND = "Course offering not found.";
    static final String COURSE_MISSING = "The selected course does not exist.";
    static final String SEMESTER_MISSING = "The selected semester does not exist.";
    static final String TAKEN = "This course offering already exists.";
    static final String FIXED = "The course and semester of an offering cannot be changed after it is created. "
            + "Create a new offering instead.";
    static final String BACK_TO_PLANNED = "An offering that has started or closed cannot go back to planned.";
    static final String REOPEN = "A closed offering cannot be made active again.";
    static final String SECTION_MISSING = "The selected section does not exist.";
    static final String SECTION_OTHER_SEMESTER = "This section belongs to a different semester.";
    static final String SECTION_INACTIVE = "This section is inactive, so it cannot take new course offerings.";
    static final String WHOLE_SEMESTER_EXISTS =
            "This course is already offered to the whole semester, so it cannot also be offered per section.";
    static final String PER_SECTION_EXISTS =
            "This course is already offered per section in this semester, so it cannot also be offered to the whole semester.";
    static final String SECTION_FIXED = "The section of an offering cannot be changed after it is created. "
            + "Create a new offering instead.";

    private final CourseOfferingRepository offerings;
    private final CourseRepository courses;
    private final SemesterRepository semesters;
    private final SectionRepository sections;
    private final OfferingFacultyLoader faculty;

    public CourseOfferingManagementService(CourseOfferingRepository offerings, CourseRepository courses,
            SemesterRepository semesters, SectionRepository sections, OfferingFacultyLoader faculty) {
        this.offerings = offerings;
        this.courses = courses;
        this.semesters = semesters;
        this.sections = sections;
        this.faculty = faculty;
    }

    /** Offerings narrowed by any of the given IDs that are not null: the latest session first. */
    @Transactional(readOnly = true)
    public List<CourseOfferingResponse> list(Long semesterId, Long courseId, Long academicSessionId, Long programId) {
        List<CourseOffering> found =
                offerings.findAllWithContext(semesterId, courseId, academicSessionId, programId, Pageable.unpaged());
        Map<Long, List<OfferingFaculty>> teaching = faculty.byOffering(found.stream().map(CourseOffering::getId).toList());
        return found.stream()
                .map(offering -> CourseOfferingResponse.of(offering, teaching.getOrDefault(offering.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseOfferingResponse get(Long courseOfferingId) {
        return CourseOfferingResponse.of(find(courseOfferingId), faculty.forOffering(courseOfferingId));
    }

    public CourseOfferingResponse create(CourseOfferingCreate request) {
        Course course = courses.findById(request.courseId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(COURSE_MISSING));
        Semester semester = semesters.findWithContextById(request.semesterId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_MISSING));
        if (course.getStatus() != CourseStatus.ACTIVE) {
            throw AcademicMasterErrors.conflict(COURSE_INACTIVE);
        }
        if (semester.getStatus() == SemesterStatus.CLOSED) {
            throw AcademicMasterErrors.conflict(SEMESTER_CLOSED);
        }
        Section section = null;
        if (request.sectionId() != null) {
            section = sections.findWithContextById(request.sectionId())
                    .orElseThrow(() -> AcademicMasterErrors.notFound(SECTION_MISSING));
            if (!section.getSemester().getId().equals(semester.getId())) {
                throw AcademicMasterErrors.conflict(SECTION_OTHER_SEMESTER);
            }
            if (section.getStatus() != SectionStatus.ACTIVE) {
                throw AcademicMasterErrors.conflict(SECTION_INACTIVE);
            }
            if (offerings.findByCourseIdAndSemesterIdAndSectionId(course.getId(), semester.getId(), section.getId()).isPresent()) {
                throw AcademicMasterErrors.conflict(TAKEN);
            }
            if (offerings.findByCourseIdAndSemesterIdAndSectionIsNull(course.getId(), semester.getId()).isPresent()) {
                throw AcademicMasterErrors.conflict(WHOLE_SEMESTER_EXISTS);
            }
        } else {
            if (offerings.findByCourseIdAndSemesterIdAndSectionIsNull(course.getId(), semester.getId()).isPresent()) {
                throw AcademicMasterErrors.conflict(TAKEN);
            }
            if (offerings.existsByCourseIdAndSemesterIdAndSectionIsNotNull(course.getId(), semester.getId())) {
                throw AcademicMasterErrors.conflict(PER_SECTION_EXISTS);
            }
        }
        CourseOffering offering = new CourseOffering(course, semester, section);
        offering.changeCapacity(request.capacity());
        if (request.registrationStatus() != null) {
            offering.changeRegistrationStatus(request.registrationStatus());
        }
        return CourseOfferingResponse.of(AcademicMasterErrors.savedOrConflict(() -> offerings.saveAndFlush(offering), TAKEN),
                List.of());
    }

    public CourseOfferingResponse update(Long courseOfferingId, CourseOfferingUpdate request) {
        CourseOffering offering = find(courseOfferingId);
        // A course or semester that is sent must be the stored one: moving an offering would silently move its
        // enrollments, teaching, attendance, results, and LMS course with it.
        if (differs(request.courseId(), offering.getCourse().getId())
                || differs(request.semesterId(), offering.getSemester().getId())) {
            throw AcademicMasterErrors.conflict(FIXED);
        }
        if (request.sectionId() != null && (offering.getSection() == null
                || !request.sectionId().equals(offering.getSection().getId()))) {
            throw AcademicMasterErrors.conflict(SECTION_FIXED);
        }
        offering.changeCapacity(request.capacity());
        if (request.registrationStatus() != null) {
            offering.changeRegistrationStatus(request.registrationStatus());
        }
        CourseOfferingStatus target = request.status();
        if (target != offering.getStatus()) {
            if (target == CourseOfferingStatus.PLANNED) {
                throw AcademicMasterErrors.conflict(BACK_TO_PLANNED);
            }
            if (target == CourseOfferingStatus.ACTIVE && offering.getStatus() == CourseOfferingStatus.CLOSED) {
                throw AcademicMasterErrors.conflict(REOPEN);
            }
            if (target == CourseOfferingStatus.ACTIVE) {
                offering.markActive();
            } else {
                offering.close();
            }
        }
        return CourseOfferingResponse.of(offerings.saveAndFlush(offering), faculty.forOffering(offering.getId()));
    }

    private static boolean differs(Long sent, Long stored) {
        return sent != null && !sent.equals(stored);
    }

    private CourseOffering find(Long courseOfferingId) {
        return offerings.findWithContextById(courseOfferingId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
