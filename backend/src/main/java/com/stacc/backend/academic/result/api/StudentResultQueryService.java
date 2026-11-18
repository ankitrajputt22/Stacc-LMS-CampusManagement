package com.stacc.backend.academic.result.api;

import java.util.List;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.result.CourseResult;
import com.stacc.backend.academic.result.CourseResultRepository;
import com.stacc.backend.academic.semester.Semester;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentResultQueryService {

    private final CourseResultRepository results;

    public StudentResultQueryService(CourseResultRepository results) {
        this.results = results;
    }

    public StudentResultsResponse getMyResults(Long userAccountId) {
        if (userAccountId == null) {
            return new StudentResultsResponse(List.of());
        }
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
