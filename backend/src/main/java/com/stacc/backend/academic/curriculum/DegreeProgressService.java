package com.stacc.backend.academic.curriculum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.stacc.backend.academic.course.CourseType;
import com.stacc.backend.academic.enrollment.SemesterEnrollment;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentRepository;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.result.AcademicStandingService;
import com.stacc.backend.academic.result.AcademicStandingService.CourseGrade;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DegreeProgressService {

    public record Requirement(CourseType courseType, BigDecimal requiredCredits) {
    }

    public record EarnedCourse(String courseCode, String courseName, BigDecimal credits, String grade) {
    }

    public enum GroupStatus { NOT_STARTED, IN_PROGRESS, COMPLETE }

    public record Group(CourseType courseType, BigDecimal requiredCredits, BigDecimal earnedCredits, BigDecimal percent,
            GroupStatus status, List<EarnedCourse> courses) {
    }

    public record Current(String academicSessionCode, int semesterNumber, String sectionCode) {
    }

    public record DegreeProgress(String loginId, String rollNumber, String programCode, String programName, String departmentName,
            String admissionSessionCode, Current current, Integer expectedGraduationYear, BigDecimal requiredCredits,
            BigDecimal earnedCredits, BigDecimal percent, List<Group> groups) {
    }

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ProgramCreditRequirementRepository requirements;
    private final ProgramRepository programs;
    private final StudentProfileRepository students;
    private final SemesterEnrollmentRepository semesterEnrollments;
    private final AcademicStandingService standing;
    private final Clock clock;

    public DegreeProgressService(ProgramCreditRequirementRepository requirements, ProgramRepository programs,
            StudentProfileRepository students, SemesterEnrollmentRepository semesterEnrollments, AcademicStandingService standing,
            Clock clock) {
        this.requirements = requirements;
        this.programs = programs;
        this.students = students;
        this.semesterEnrollments = semesterEnrollments;
        this.standing = standing;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Requirement> requirementsOf(Long programId) {
        programOf(programId);
        return requirements.findAllByProgramIdOrderByIdAsc(programId).stream()
                .map(requirement -> new Requirement(requirement.getCourseType(), requirement.getRequiredCredits())).toList();
    }

    public List<Requirement> set(Long programId, List<Requirement> wanted) {
        Program program = programs.findByIdForUpdate(programId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Program not found."));
        Set<CourseType> seen = new HashSet<>();
        for (Requirement requirement : wanted) {
            if (requirement.courseType() == null || !seen.add(requirement.courseType())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "List each course type once.");
            }
            BigDecimal credits = requirement.requiredCredits();
            if (credits == null || credits.signum() <= 0 || credits.compareTo(BigDecimal.valueOf(400)) > 0
                    || credits.stripTrailingZeros().scale() > 2) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Required credits are above 0 and at most 400, with up to 2 decimals.");
            }
        }
        Map<CourseType, ProgramCreditRequirement> existing = new EnumMap<>(CourseType.class);
        requirements.findAllByProgramIdOrderByIdAsc(programId).forEach(row -> existing.put(row.getCourseType(), row));
        var now = clock.instant();
        for (Requirement requirement : wanted) {
            ProgramCreditRequirement row = existing.remove(requirement.courseType());
            if (row == null) {
                requirements.save(new ProgramCreditRequirement(program, requirement.courseType(), requirement.requiredCredits(), now));
            } else {
                row.change(requirement.requiredCredits(), now);
            }
        }
        requirements.flush();
        return requirementsOf(programId);
    }

    @Transactional(readOnly = true)
    public DegreeProgress mine(Long userAccountId) {
        StudentProfile profile = students.findWithContextByUserAccountId(userAccountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No student profile is recorded for this account."));
        Program program = profile.getProgram();
        Map<CourseType, List<EarnedCourse>> earned = new EnumMap<>(CourseType.class);
        Set<String> counted = new HashSet<>();
        for (var semester : standing.standing(userAccountId).semesters()) {
            for (CourseGrade grade : semester.courses()) {
                if (grade.passed() && counted.add(grade.courseCode())) {
                    earned.computeIfAbsent(grade.courseType(), type -> new ArrayList<>())
                            .add(new EarnedCourse(grade.courseCode(), grade.courseName(), grade.credits(), grade.grade()));
                }
            }
        }
        List<Group> groups = new ArrayList<>();
        BigDecimal required = BigDecimal.ZERO;
        BigDecimal earnedTotal = BigDecimal.ZERO;
        for (ProgramCreditRequirement requirement : requirements.findAllByProgramIdOrderByIdAsc(program.getId())) {
            List<EarnedCourse> courses = earned.getOrDefault(requirement.getCourseType(), List.of());
            BigDecimal credits = courses.stream().map(EarnedCourse::credits).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal counts = credits.min(requirement.getRequiredCredits());
            GroupStatus status = credits.signum() == 0 ? GroupStatus.NOT_STARTED
                    : counts.compareTo(requirement.getRequiredCredits()) >= 0 ? GroupStatus.COMPLETE : GroupStatus.IN_PROGRESS;
            groups.add(new Group(requirement.getCourseType(), requirement.getRequiredCredits(), credits,
                    percent(counts, requirement.getRequiredCredits()), status, courses));
            required = required.add(requirement.getRequiredCredits());
            earnedTotal = earnedTotal.add(counts);
        }
        Current current = semesterEnrollments.findAllWithContextByStudentProfileId(profile.getId()).stream()
                .filter(enrollment -> enrollment.getStatus() == SemesterEnrollmentStatus.ENROLLED).findFirst()
                .map(DegreeProgressService::currentOf).orElse(null);
        var admission = profile.getAdmissionSession();
        Integer graduation = admission.getStartDate() == null ? null
                : admission.getStartDate().getYear() + (program.getDurationSemesters() + 1) / 2;
        return new DegreeProgress(profile.getUserAccount().getLoginId(), profile.getRollNumber(), program.getCode(), program.getName(),
                program.getDepartment().getName(), admission.getCode(), current, graduation, required, earnedTotal,
                required.signum() == 0 ? null : percent(earnedTotal, required), groups);
    }

    private static Current currentOf(SemesterEnrollment enrollment) {
        return new Current(enrollment.getSemester().getAcademicSession().getCode(), enrollment.getSemester().getSemesterNumber(),
                enrollment.getSection() == null ? null : enrollment.getSection().getCode());
    }

    private Program programOf(Long programId) {
        return Optional.ofNullable(programId).flatMap(programs::findById)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Program not found."));
    }

    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
