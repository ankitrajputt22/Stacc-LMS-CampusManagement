package com.stacc.backend.lms.outcome;

import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course learning outcomes. Students read the active outcomes of a course the student access policy allows; faculty read
 * all and add, correct, or archive them under the faculty access policy, finding the course from the stored outcome. A
 * new outcome takes the next number of its course under the course row lock, so two at once never share a code.
 */
@Service
public class LmsOutcomeService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String OUTCOME_UNAVAILABLE = "Outcome not found or unavailable.";
    public static final String OUTCOME_ARCHIVED = "This outcome is archived and can no longer be changed.";

    private final LmsCourseOutcomeRepository outcomes;
    private final LmsCourseRepository lmsCourses;
    private final LmsStudentAccessService studentAccess;
    private final LmsFacultyAccessService facultyAccess;

    public LmsOutcomeService(LmsCourseOutcomeRepository outcomes, LmsCourseRepository lmsCourses,
            LmsStudentAccessService studentAccess, LmsFacultyAccessService facultyAccess) {
        this.outcomes = outcomes;
        this.lmsCourses = lmsCourses;
        this.studentAccess = studentAccess;
        this.facultyAccess = facultyAccess;
    }

    @Transactional(readOnly = true)
    public List<LmsCourseOutcome> forStudent(Long userAccountId, Long lmsCourseId) {
        if (!studentAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
        return outcomes.findAllByLmsCourseIdAndStatusOrderByNumberAsc(lmsCourseId, LmsOutcomeStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<LmsCourseOutcome> forFaculty(Long userAccountId, Long lmsCourseId) {
        requireFaculty(userAccountId, lmsCourseId);
        return outcomes.findAllByLmsCourseIdOrderByNumberAsc(lmsCourseId);
    }

    /** Adds the course's next outcome, numbered after every earlier one, archived ones included. */
    @Transactional
    public LmsCourseOutcome add(Long userAccountId, Long lmsCourseId, String description) {
        requireFaculty(userAccountId, lmsCourseId);
        LmsCourse lmsCourse = lmsCourses.findByIdForUpdate(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        int next = outcomes.findAllByLmsCourseIdForUpdate(lmsCourseId).stream()
                .mapToInt(LmsCourseOutcome::getNumber).max().orElse(0) + 1;
        return outcomes.saveAndFlush(new LmsCourseOutcome(lmsCourse, next, description));
    }

    @Transactional
    public LmsCourseOutcome describe(Long userAccountId, Long outcomeId, String description) {
        LmsCourseOutcome outcome = manageable(userAccountId, outcomeId);
        if (!outcome.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, OUTCOME_ARCHIVED);
        }
        outcome.describe(description);
        return outcome;
    }

    /** Archives the outcome. Assessments aligned to it keep the link; nothing new can be aligned to it. */
    @Transactional
    public LmsCourseOutcome archive(Long userAccountId, Long outcomeId) {
        LmsCourseOutcome outcome = manageable(userAccountId, outcomeId);
        outcome.archive();
        return outcome;
    }

    // The course comes from the stored outcome, never from the client.
    private LmsCourseOutcome manageable(Long userAccountId, Long outcomeId) {
        LmsCourseOutcome outcome = outcomeId == null ? null : outcomes.findWithCourseById(outcomeId).orElse(null);
        if (outcome == null || !facultyAccess.canAccessCourse(userAccountId, outcome.getLmsCourse().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, OUTCOME_UNAVAILABLE);
        }
        return outcome;
    }

    private void requireFaculty(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }
}
