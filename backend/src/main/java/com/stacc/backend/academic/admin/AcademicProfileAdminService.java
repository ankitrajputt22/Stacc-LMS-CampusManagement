package com.stacc.backend.academic.admin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.stacc.backend.academic.admin.AcademicProfileRecords.CurrentPlacement;
import com.stacc.backend.academic.admin.AcademicProfileRecords.FacultyProfileSummary;
import com.stacc.backend.academic.admin.AcademicProfileRecords.FacultyProfileUpdate;
import com.stacc.backend.academic.admin.AcademicProfileRecords.StudentProfileSummary;
import com.stacc.backend.academic.admin.AcademicProfileRecords.StudentProfileUpdate;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.department.DepartmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins find students and faculty and correct their official academic mapping: a student's roll number and profile
 * status, a faculty member's primary department and profile status. Accounts, names, contact details, a student's
 * program, and their enrollments are not changed here. Nothing is deleted.
 */
@Service
@Transactional
public class AcademicProfileAdminService {

    static final int LIMIT = 50;
    static final String STUDENT_NOT_FOUND = "Student profile not found.";
    static final String FACULTY_NOT_FOUND = "Faculty profile not found.";
    static final String ROLL_TAKEN = "Another student already has this roll number.";
    static final String DEPARTMENT_MISSING = "The selected department does not exist.";
    static final String DEPARTMENT_INACTIVE = "This department is inactive, so it cannot take faculty members.";

    private final StudentProfileRepository students;
    private final FacultyProfileRepository faculty;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final TeachingAssignmentRepository assignments;
    private final DepartmentRepository departments;
    private final OfferingFacultyLoader names;

    public AcademicProfileAdminService(StudentProfileRepository students, FacultyProfileRepository faculty,
            SemesterEnrollmentRepository semesterEnrollments, TeachingAssignmentRepository assignments,
            DepartmentRepository departments, OfferingFacultyLoader names) {
        this.students = students;
        this.faculty = faculty;
        this.semesterEnrollments = semesterEnrollments;
        this.assignments = assignments;
        this.departments = departments;
        this.names = names;
    }

    /** Students whose login ID or roll number contains the text, by login ID, at most 50. Empty text lists the first 50. */
    @Transactional(readOnly = true)
    public List<StudentProfileSummary> students(String query) {
        List<StudentProfile> found = students.searchByLoginIdOrRollNumber(contains(query), PageRequest.of(0, LIMIT));
        return summarize(found);
    }

    public StudentProfileSummary updateStudent(Long studentProfileId, StudentProfileUpdate request) {
        StudentProfile profile = students.findById(studentProfileId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(STUDENT_NOT_FOUND));
        String roll = request.rollNumber() == null || request.rollNumber().isBlank() ? null
                : request.rollNumber().strip().toUpperCase(Locale.ROOT);
        if (roll != null && students.existsByRollNumberAndIdNot(roll, profile.getId())) {
            throw AcademicMasterErrors.conflict(ROLL_TAKEN);
        }
        profile.assignRollNumber(roll);
        if (request.status() == StudentProfileStatus.ACTIVE) {
            profile.activate();
        } else {
            profile.deactivate();
        }
        return summarize(List.of(AcademicMasterErrors.savedOrConflict(() -> students.saveAndFlush(profile), ROLL_TAKEN)))
                .getFirst();
    }

    /** Faculty whose login ID contains the text, by login ID, at most 50, with how many offerings each teaches now. */
    @Transactional(readOnly = true)
    public List<FacultyProfileSummary> faculty(String query) {
        return summarizeFaculty(faculty.searchByLoginId(contains(query), PageRequest.of(0, LIMIT)));
    }

    public FacultyProfileSummary updateFaculty(Long facultyProfileId, FacultyProfileUpdate request) {
        FacultyProfile profile = faculty.findWithAccountById(facultyProfileId)
                .orElseThrow(() -> AcademicMasterErrors.notFound(FACULTY_NOT_FOUND));
        if (!request.departmentId().equals(profile.getDepartment().getId())) {
            Department department = departments.findById(request.departmentId())
                    .orElseThrow(() -> AcademicMasterErrors.badRequest(DEPARTMENT_MISSING));
            if (department.getStatus() != DepartmentStatus.ACTIVE) {
                throw AcademicMasterErrors.conflict(DEPARTMENT_INACTIVE);
            }
            profile.moveToDepartment(department);
        }
        if (request.status() == FacultyProfileStatus.ACTIVE) {
            profile.activate();
        } else {
            profile.deactivate();
        }
        faculty.saveAndFlush(profile);
        return summarizeFaculty(List.of(profile)).getFirst();
    }

    private List<StudentProfileSummary> summarize(List<StudentProfile> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, CurrentPlacement> current = new LinkedHashMap<>();
        for (SemesterEnrollment enrollment : semesterEnrollments.findEnrolledWithContextByStudentProfileIdIn(
                found.stream().map(StudentProfile::getId).toList())) {
            current.putIfAbsent(enrollment.getStudentProfile().getId(), new CurrentPlacement(
                    enrollment.getSemester().getAcademicSession().getCode(), enrollment.getSemester().getSemesterNumber(),
                    enrollment.getSection() == null ? null : enrollment.getSection().getCode()));
        }
        Map<Long, String> people = names.namesOfAccounts(found.stream().map(profile -> profile.getUserAccount().getId()).toList());
        return found.stream().map(profile -> new StudentProfileSummary(profile.getId(), profile.getUserAccount().getLoginId(),
                people.get(profile.getUserAccount().getId()), profile.getRollNumber(), profile.getProgram().getId(),
                profile.getProgram().getCode(), profile.getProgram().getName(), profile.getProgram().getDepartment().getCode(),
                profile.getAdmissionSession().getCode(), profile.getStatus().name(), current.get(profile.getId())))
                .toList();
    }

    private List<FacultyProfileSummary> summarizeFaculty(List<FacultyProfile> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> teaching = new HashMap<>();
        for (Object[] row : assignments.countActiveByFacultyProfileIdIn(found.stream().map(FacultyProfile::getId).toList())) {
            teaching.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, String> people = names.namesOfAccounts(found.stream().map(profile -> profile.getUserAccount().getId()).toList());
        return found.stream().map(profile -> new FacultyProfileSummary(profile.getId(), profile.getUserAccount().getLoginId(),
                people.get(profile.getUserAccount().getId()), profile.getDepartment().getId(), profile.getDepartment().getCode(),
                profile.getDepartment().getName(), profile.getStatus().name(), teaching.getOrDefault(profile.getId(), 0L)))
                .toList();
    }

    private static String contains(String query) {
        String text = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
