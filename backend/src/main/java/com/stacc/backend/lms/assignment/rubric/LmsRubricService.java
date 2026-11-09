package com.stacc.backend.lms.assignment.rubric;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rubrics of a course, for the faculty who teach it. Every action checks the faculty access policy; for an existing
 * rubric the course comes from the stored rubric. A change locks the rubric row first. A rubric already used for
 * grading keeps its criteria (409), because grades keep their scores per criterion; it can still be archived.
 * Grading reads the rubric with a shared lock, so an edit and a grade never cross.
 */
@Service
public class LmsRubricService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String RUBRIC_UNAVAILABLE = "Rubric not found or unavailable.";
    public static final String RUBRIC_ARCHIVED = "This rubric is archived and can no longer be changed.";
    public static final String RUBRIC_IN_USE = "This rubric has been used for grading, so its criteria stay as they are. Make a new rubric instead.";

    private final LmsRubricRepository rubrics;
    private final LmsCourseRepository lmsCourses;
    private final LmsFacultyAccessService facultyAccess;

    public LmsRubricService(LmsRubricRepository rubrics, LmsCourseRepository lmsCourses, LmsFacultyAccessService facultyAccess) {
        this.rubrics = rubrics;
        this.lmsCourses = lmsCourses;
        this.facultyAccess = facultyAccess;
    }

    /** The course's rubrics, archived ones included, newest first, with those already used for grading. */
    public record RubricList(List<LmsRubric> rubrics, Set<Long> inUse) {
    }

    @Transactional(readOnly = true)
    public RubricList list(Long userAccountId, Long lmsCourseId) {
        requireCourse(userAccountId, lmsCourseId);
        List<LmsRubric> found = rubrics.findAllForCourse(lmsCourseId);
        found.forEach(rubric -> rubric.getCriteria().forEach(criterion -> criterion.getLevels().size()));
        return new RubricList(found, new HashSet<>(rubrics.findIdsInUse(lmsCourseId)));
    }

    @Transactional
    public LmsRubric create(Long userAccountId, Long lmsCourseId, RubricContent content) {
        requireCourse(userAccountId, lmsCourseId);
        LmsCourse course = lmsCourses.findById(lmsCourseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        return rubrics.saveAndFlush(valid(() -> new LmsRubric(course, userAccountId, content)));
    }

    /** Replaces the title, description, and criteria of a rubric not yet used for grading. */
    @Transactional
    public LmsRubric edit(Long userAccountId, Long rubricId, RubricContent content) {
        LmsRubric rubric = locked(userAccountId, rubricId);
        if (!rubric.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, RUBRIC_ARCHIVED);
        }
        // A locking read: a grade saved while this request waited for the rubric lock is seen.
        if (!rubrics.findUseForShare(rubricId).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, RUBRIC_IN_USE);
        }
        valid(() -> {
            rubric.edit(content);
            return rubric;
        });
        return rubrics.saveAndFlush(rubric);
    }

    /** Archives the rubric; assignments that use it keep it. Archiving again changes nothing; nothing restores it. */
    @Transactional
    public LmsRubric archive(Long userAccountId, Long rubricId) {
        LmsRubric rubric = locked(userAccountId, rubricId);
        rubric.archive();
        return rubrics.saveAndFlush(rubric);
    }

    @Transactional(readOnly = true)
    public boolean isInUse(Long rubricId) {
        return rubrics.findLmsCourseIdById(rubricId).map(rubrics::findIdsInUse).map(ids -> ids.contains(rubricId)).orElse(false);
    }

    private LmsRubric locked(Long userAccountId, Long rubricId) {
        Long lmsCourseId = rubricId == null ? null : rubrics.findLmsCourseIdById(rubricId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, RUBRIC_UNAVAILABLE);
        }
        // The rubric row first, alone; only then its criteria (see LmsRubricRepository#lockRowForUpdate).
        rubrics.lockRowForUpdate(rubricId);
        return rubrics.findByIdForUpdate(rubricId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, RUBRIC_UNAVAILABLE));
    }

    private void requireCourse(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }

    private static <T> T valid(Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }
}
