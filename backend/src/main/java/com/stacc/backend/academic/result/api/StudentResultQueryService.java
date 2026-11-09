package com.stacc.backend.academic.result.api;

import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.semester.Semester;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in student's own published course results. They are found through the student's own course
 * enrollments, starting from the account in the access token, so no ID from the client plays a part. A result
 * stays visible after its enrollment is completed or withdrawn. No LMS course or membership is needed.
 */
@Service
@Transactional(readOnly = true)
public class StudentResultQueryService {

    private final CourseResultRepository results;

    public StudentResultQueryService(CourseResultRepository results) {
        this.results = results;
    }

    /**
     * Published results only, the latest academic session first, then the highest semester number, then course code.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public StudentResultsResponse getMyResults(Long userAccountId) {
        if (userAccountId == null) {
            return new StudentResultsResponse(List.of());
        }
        // The query only returns published results. Checked again here so a draft can never slip in.
        return new StudentResultsResponse(results.findAllPublishedByUserAccountId(userAccountId).stream()
                .filter(CourseResult::isPublished)
                .map(StudentResultQueryService::resultOf)
                .toList());
    }

    private static StudentResultsResponse.Result resultOf(CourseResult result) {
        CourseOffering offering = result.getCourseEnrollment().getCourseOffering();
        Course course = offering.getCourse();
        Semester semester = offering.getSemester();
        return new StudentResultsResponse.Result(
                course.getCode(),
                course.getName(),
                course.getCredits(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode(),
                result.getMarksObtained(),
                result.getMaxMarks(),
                result.getGradeCode(),
                result.getRemarks(),
                result.getPublishedAt());
    }
}
