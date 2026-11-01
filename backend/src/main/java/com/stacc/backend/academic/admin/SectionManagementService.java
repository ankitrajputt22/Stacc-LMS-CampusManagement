package com.stacc.backend.academic.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.stacc.backend.academic.admin.AcademicMasterRequests.SectionCreate;
import com.stacc.backend.academic.admin.AcademicMasterRequests.SectionUpdate;
import com.stacc.backend.academic.admin.AcademicMasterResponses.SectionResponse;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.section.Section;
import com.stacc.backend.academic.section.SectionRepository;
import com.stacc.backend.academic.section.SectionStatus;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.semester.SemesterStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins list, create, and edit the student sections of each semester. A section's semester and code are set once;
 * its name, capacity, and status can change. Placing students in a section belongs to official enrollment
 * ({@link AcademicEnrollmentManagementService#changeSection}). Nothing is ever deleted.
 */
@Service
@Transactional
public class SectionManagementService {

    static final String NOT_FOUND = "Section not found.";
    static final String SEMESTER_MISSING = "The selected semester does not exist.";
    static final String SEMESTER_CLOSED = "This semester is closed, so it cannot take new sections.";
    static final String CODE_TAKEN = "This semester already has a section with this code.";

    private final SectionRepository sections;
    private final SemesterRepository semesters;
    private final SemesterEnrollmentRepository enrollments;

    public SectionManagementService(SectionRepository sections, SemesterRepository semesters,
            SemesterEnrollmentRepository enrollments) {
        this.sections = sections;
        this.semesters = semesters;
        this.enrollments = enrollments;
    }

    /** Every section, or one semester's when a semester ID is given, each with how many students are enrolled in it. */
    @Transactional(readOnly = true)
    public List<SectionResponse> list(Long semesterId) {
        List<Section> found = semesterId == null ? sections.findAllWithContext()
                : sections.findAllWithContextBySemesterId(semesterId);
        Map<Long, Long> counts = enrolledCounts();
        return found.stream().map(section -> SectionResponse.of(section, counts.getOrDefault(section.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public SectionResponse get(Long sectionId) {
        Section section = find(sectionId);
        return SectionResponse.of(section, enrolledCounts().getOrDefault(section.getId(), 0L));
    }

    public SectionResponse create(SectionCreate request) {
        Semester semester = semesters.findWithContextById(request.semesterId())
                .orElseThrow(() -> AcademicMasterErrors.badRequest(SEMESTER_MISSING));
        if (semester.getStatus() == SemesterStatus.CLOSED) {
            throw AcademicMasterErrors.conflict(SEMESTER_CLOSED);
        }
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (sections.existsBySemesterIdAndCode(semester.getId(), code)) {
            throw AcademicMasterErrors.conflict(CODE_TAKEN);
        }
        Section section = AcademicMasterErrors.savedOrConflict(
                () -> sections.saveAndFlush(new Section(semester, code, request.name(), request.capacity())), CODE_TAKEN);
        return SectionResponse.of(section, 0);
    }

    /**
     * Changes the name, capacity, and status. The section is locked first, as placing a student does, so the capacity
     * is never set below the students enrolled in it.
     */
    public SectionResponse update(Long sectionId, SectionUpdate request) {
        Section section = sections.findByIdForUpdate(sectionId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
        long enrolled = enrollments.countEnrolledInSectionForShare(section.getId());
        if (request.capacity() != null && request.capacity() < enrolled) {
            throw AcademicMasterErrors.conflict("This section already has " + enrolled
                    + " enrolled students, so its capacity cannot be lower than that.");
        }
        section.rename(request.name());
        section.changeCapacity(request.capacity());
        if (request.status() == SectionStatus.ACTIVE) {
            section.activate();
        } else {
            section.deactivate();
        }
        sections.saveAndFlush(section);
        return SectionResponse.of(find(sectionId), enrolled);
    }

    private Section find(Long sectionId) {
        return sections.findWithContextById(sectionId).orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
    }

    private Map<Long, Long> enrolledCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : enrollments.countEnrolledBySection()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
