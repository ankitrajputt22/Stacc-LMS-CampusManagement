package com.stacc.backend.academic.admin;

import java.util.List;
import java.util.Map;

import com.stacc.backend.academic.admin.FacultyAssignmentRequests.FacultyAssignmentCreate;
import com.stacc.backend.academic.admin.FacultyAssignmentRequests.FacultyAssignmentUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.FacultyLookupResponse;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.OfferingFaculty;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.academic.teaching.TeachingRole;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FacultyAssignmentService {

    static final int LOOKUP_LIMIT = 50;
    static final String OFFERING_MISSING = "The selected course offering does not exist.";
    static final String FACULTY_MISSING = "The selected faculty member does not exist.";
    static final String NOT_FOUND = "Faculty assignment not found.";
    static final String OFFERING_CLOSED = "This course offering is closed, so who teaches it can no longer change.";
    static final String FACULTY_INACTIVE = "This faculty member's profile or account is inactive, so they cannot be assigned.";
    static final String NOT_FACULTY = "This account does not hold the faculty role, so it cannot be assigned to teach.";
    static final String ALREADY_ASSIGNED = "This faculty member already teaches this course offering.";
    static final String PRIMARY_TAKEN = "This course offering already has a primary faculty member. "
            + "Assign this one as co-faculty, or change the current primary first.";

    private final CourseOfferingRepository offerings;
    private final FacultyProfileRepository facultyProfiles;
    private final TeachingAssignmentRepository assignments;
    private final OfferingFacultyLoader loader;

    public FacultyAssignmentService(CourseOfferingRepository offerings, FacultyProfileRepository facultyProfiles,
            TeachingAssignmentRepository assignments, OfferingFacultyLoader loader) {
        this.offerings = offerings;
        this.facultyProfiles = facultyProfiles;
        this.assignments = assignments;
        this.loader = loader;
    }

    @Transactional(readOnly = true)
    public List<OfferingFaculty> forOffering(Long courseOfferingId) {
        if (!offerings.existsById(courseOfferingId)) {
            throw AcademicMasterErrors.notFound(OFFERING_MISSING);
        }
        return loader.forOffering(courseOfferingId);
    }

    @Transactional(readOnly = true)
    public List<FacultyLookupResponse> lookup(String query) {
        String text = query == null ? "" : query.strip().toLowerCase(java.util.Locale.ROOT);
        String contains = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        List<FacultyProfile> found = facultyProfiles.searchByLoginId(contains, PageRequest.of(0, LOOKUP_LIMIT));
        Map<Long, String> names = loader.namesOfAccounts(found.stream().map(profile -> profile.getUserAccount().getId()).toList());
        return found.stream().map(profile -> new FacultyLookupResponse(profile.getId(), profile.getUserAccount().getLoginId(),
                names.get(profile.getUserAccount().getId()), profile.getDepartment().getCode(), profile.getStatus().name()))
                .toList();
    }

    public OfferingFaculty assign(FacultyAssignmentCreate request) {
        CourseOffering offering = lockOpenOffering(request.courseOfferingId());
        FacultyProfile faculty = facultyProfiles.findWithAccountById(request.facultyProfileId())
                .orElseThrow(() -> AcademicMasterErrors.notFound(FACULTY_MISSING));
        if (faculty.getStatus() != FacultyProfileStatus.ACTIVE || faculty.getUserAccount().getStatus() != AccountStatus.ACTIVE) {
            throw AcademicMasterErrors.conflict(FACULTY_INACTIVE);
        }
        if (!faculty.getUserAccount().hasRole(RoleName.FACULTY)) {
            throw AcademicMasterErrors.conflict(NOT_FACULTY);
        }
        TeachingAssignment assignment = assignments
                .findByFacultyProfileIdAndCourseOfferingId(faculty.getId(), offering.getId()).orElse(null);
        if (assignment != null && assignment.getStatus() == TeachingAssignmentStatus.ACTIVE) {
            throw AcademicMasterErrors.conflict(ALREADY_ASSIGNED);
        }
        requireNoOtherPrimary(offering.getId(), request.role(), assignment == null ? -1L : assignment.getId());
        if (assignment == null) {
            assignment = new TeachingAssignment(faculty, offering);
        } else {
            assignment.activate();
        }
        assignment.changeRole(request.role());
        TeachingAssignment saved = assignments.saveAndFlush(assignment);
        return loader.describe(assignments.findWithFacultyById(saved.getId()).orElseThrow());
    }

    public OfferingFaculty update(Long teachingAssignmentId, FacultyAssignmentUpdate request) {
        TeachingAssignment assignment = assignments.findWithFacultyById(teachingAssignmentId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(NOT_FOUND));
        lockOpenOffering(assignment.getCourseOffering().getId());
        if (request.status() == TeachingAssignmentStatus.ACTIVE) {
            FacultyProfile faculty = assignment.getFacultyProfile();
            if (assignment.getStatus() != TeachingAssignmentStatus.ACTIVE
                    && (faculty.getStatus() != FacultyProfileStatus.ACTIVE
                            || faculty.getUserAccount().getStatus() != AccountStatus.ACTIVE)) {
                throw AcademicMasterErrors.conflict(FACULTY_INACTIVE);
            }
            requireNoOtherPrimary(assignment.getCourseOffering().getId(), request.role(), assignment.getId());
            assignment.activate();
        } else {
            assignment.deactivate();
        }
        assignment.changeRole(request.role());
        assignments.saveAndFlush(assignment);
        return loader.describe(assignment);
    }

    private CourseOffering lockOpenOffering(Long courseOfferingId) {
        CourseOffering offering = offerings.findByIdForUpdate(courseOfferingId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(OFFERING_MISSING));
        if (offering.getStatus() == CourseOfferingStatus.CLOSED) {
            throw AcademicMasterErrors.conflict(OFFERING_CLOSED);
        }
        return offering;
    }

    private void requireNoOtherPrimary(Long courseOfferingId, TeachingRole role, Long exceptAssignmentId) {
        if (role == TeachingRole.PRIMARY && assignments.existsByCourseOfferingIdAndRoleAndStatusAndIdNot(
                courseOfferingId, TeachingRole.PRIMARY, TeachingAssignmentStatus.ACTIVE, exceptAssignmentId)) {
            throw AcademicMasterErrors.conflict(PRIMARY_TAKEN);
        }
    }
}
