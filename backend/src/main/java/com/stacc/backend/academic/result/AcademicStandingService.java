package com.stacc.backend.academic.result;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.course.CourseType;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A student's standing (v0.16.3), worked out on every read from their published results and the grade scale, never
 * stored: each course's grade and points, each semester's SGPA, and the CGPA, all weighted by course credits. Audit
 * courses carry no points. A result whose grade code the scale does not know is shown but left out of the averages.
 */
@Service
@Transactional(readOnly = true)
public class AcademicStandingService {

    public record CourseGrade(String courseCode, String courseName, CourseType courseType, BigDecimal credits,
            BigDecimal internalMarks, BigDecimal internalMax, BigDecimal externalMarks, BigDecimal externalMax,
            BigDecimal marksObtained, BigDecimal maxMarks, String grade, BigDecimal gradePoints, boolean passed) {
    }

    public record SemesterStanding(String academicSessionCode, String programCode, int semesterNumber, BigDecimal creditsGraded,
            BigDecimal creditsEarned, BigDecimal sgpa, List<CourseGrade> courses) {
    }

    /** Semesters latest first; the CGPA is null until some graded credit exists. */
    public record Standing(List<SemesterStanding> semesters, BigDecimal cgpa, BigDecimal creditsEarned) {
    }

    private final CourseResultRepository results;
    private final GradeScaleRepository scale;

    public AcademicStandingService(CourseResultRepository results, GradeScaleRepository scale) {
        this.results = results;
        this.scale = scale;
    }

    public GradeScale scale() {
        return GradeScale.of(scale.findAllByOrderByPositionAsc());
    }

    /** @param userAccountId the signed-in account, from the access token */
    public Standing standing(Long userAccountId) {
        GradeScale grades = scale();
        Map<String, List<CourseResult>> bySemester = new LinkedHashMap<>();
        for (CourseResult result : results.findAllPublishedByUserAccountId(userAccountId)) {
            Semester semester = result.getCourseEnrollment().getCourseOffering().getSemester();
            bySemester.computeIfAbsent(semester.getId() + "", key -> new ArrayList<>()).add(result);
        }
        List<SemesterStanding> semesters = new ArrayList<>();
        BigDecimal totalPoints = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal earned = BigDecimal.ZERO;
        for (List<CourseResult> group : bySemester.values()) {
            Semester semester = group.getFirst().getCourseEnrollment().getCourseOffering().getSemester();
            BigDecimal points = BigDecimal.ZERO;
            BigDecimal credits = BigDecimal.ZERO;
            BigDecimal semesterEarned = BigDecimal.ZERO;
            List<CourseGrade> courses = new ArrayList<>();
            for (CourseResult result : group) {
                CourseGrade grade = gradeOf(result, grades);
                courses.add(grade);
                if (grade.gradePoints() != null && grade.courseType() != CourseType.AUDIT) {
                    points = points.add(grade.gradePoints().multiply(grade.credits()));
                    credits = credits.add(grade.credits());
                }
                if (grade.passed()) {
                    semesterEarned = semesterEarned.add(grade.credits());
                }
            }
            totalPoints = totalPoints.add(points);
            totalCredits = totalCredits.add(credits);
            earned = earned.add(semesterEarned);
            semesters.add(new SemesterStanding(semester.getAcademicSession().getCode(), semester.getProgram().getCode(),
                    semester.getSemesterNumber(), credits, semesterEarned, average(points, credits), courses));
        }
        return new Standing(semesters, average(totalPoints, totalCredits), earned);
    }

    /** One published result with its grade and points under the scale. */
    public static CourseGrade gradeOf(CourseResult result, GradeScale grades) {
        CourseOffering offering = result.getCourseEnrollment().getCourseOffering();
        Course course = offering.getCourse();
        var grade = grades.gradeOf(result.getGradeCode(), result.getMarksObtained(), result.getMaxMarks());
        String shown = result.getGradeCode() != null ? result.getGradeCode() : grade.map(GradeScale.Grade::code).orElse(null);
        boolean passed = grade.map(GradeScale.Grade::passing).orElse(false);
        return new CourseGrade(course.getCode(), course.getName(), course.getCourseType(), course.getCredits(),
                result.getInternalMarks(), result.getInternalMax(), result.getExternalMarks(), result.getExternalMax(),
                result.getMarksObtained(), result.getMaxMarks(), shown, grade.map(GradeScale.Grade::points).orElse(null), passed);
    }

    private static BigDecimal average(BigDecimal points, BigDecimal credits) {
        return credits.signum() == 0 ? null : points.divide(credits, 2, RoundingMode.HALF_UP);
    }
}
