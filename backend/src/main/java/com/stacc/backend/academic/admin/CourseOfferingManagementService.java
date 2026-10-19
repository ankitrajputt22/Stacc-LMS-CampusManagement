package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseOfferingResponse;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.semester.Semester;
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

    static final String NOT_FOUND = "Course offering not found.";
    static final String COURSE_MISSING = "The selected course does not exist.";
    static final String SEMESTER_MISSING = "The selected semester does not exist.";
    static final String TAKEN = "This course offering already exists.";
    static final String FIXED = "The course and semester of an offering cannot be changed after it is created. "
            + "Create a new offering instead.";
    static final String BACK_TO_PLANNED = "An offering that has started or closed cannot go back to planned.";
    static final String REOPEN = "A closed offering cannot be made active again.";

    private final CourseOfferingRepository offerings;
    private final CourseRepository courses;
    private final SemesterRepository semesters;

    public CourseOfferingManagementService(CourseOfferingRepository offerings, CourseRepository courses,
            SemesterRepository semesters) {
        this.offerings = offerings;
        this.courses = courses;
        this.semesters = semesters;
    }

    /** Offerings narrowed by any of the given IDs that are not null: the latest session first. */
    @Transactional(readOnly = true)
    public List<CourseOfferingResponse> list(Long semesterId, Long courseId, Long academicSessionId, Long programId) {
        return offerings.findAllWithContext(semesterId, courseId, academicSessionId, programId, Pageable.unpaged()).stream()
                .map(CourseOfferingResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseOfferingResponse get(Long courseOfferingId) {
        return CourseOfferingResponse.of(find(courseOfferingId));
    }

    public CourseOfferingResponse create(CourseOfferingCreate request) {
        Course course = courses.findById(request.courseId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(COURSE_MISSING));
        Semester semester = semesters.findWithContextById(request.semesterId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(SEMESTER_MISSING));
        if (offerings.findByCourseIdAndSemesterId(course.getId(), semester.getId()).isPresent()) {
            throw AcademicMasterErrors.conflict(TAKEN);
        }
        return CourseOfferingResponse.of(AcademicMasterErrors.savedOrConflict(
                () -> offerings.saveAndFlush(new CourseOffering(course, semester)), TAKEN));
    }

    public CourseOfferingResponse update(Long courseOfferingId, CourseOfferingUpdate request) {
        CourseOffering offering = find(courseOfferingId);
        // A course or semester that is sent must be the stored one: moving an offering would silently move its
        // enrollments, teaching, attendance, results, and LMS course with it.
        if (differs(request.courseId(), offering.getCourse().getId())
                || differs(request.semesterId(), offering.getSemester().getId())) {
            throw AcademicMasterErrors.conflict(FIXED);
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
        return CourseOfferingResponse.of(offerings.saveAndFlush(offering));
    }

    private static boolean differs(Long sent, Long stored) {
        return sent != null && !sent.equals(stored);
    }

    private CourseOffering find(Long courseOfferingId) {
        return offerings.findWithContextById(courseOfferingId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }
}
