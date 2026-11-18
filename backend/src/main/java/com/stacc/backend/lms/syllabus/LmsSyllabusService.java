package com.stacc.backend.lms.syllabus;

import java.time.Clock;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsSyllabusService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String NOTHING_TO_PUBLISH = "Save a syllabus draft before publishing it.";

    private final LmsSyllabusRepository syllabi;
    private final LmsCourseRepository lmsCourses;
    private final LmsStudentAccessService studentAccess;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;

    public LmsSyllabusService(LmsSyllabusRepository syllabi, LmsCourseRepository lmsCourses,
            LmsStudentAccessService studentAccess, LmsFacultyAccessService facultyAccess, Clock clock) {
        this.syllabi = syllabi;
        this.lmsCourses = lmsCourses;
        this.studentAccess = studentAccess;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<LmsSyllabus> forStudent(Long userAccountId, Long lmsCourseId) {
        if (!studentAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw unavailable();
        }
        return syllabi.findByLmsCourseId(lmsCourseId).filter(LmsSyllabus::isPublished);
    }

    @Transactional(readOnly = true)
    public Optional<LmsSyllabus> forFaculty(Long userAccountId, Long lmsCourseId) {
        requireFaculty(userAccountId, lmsCourseId);
        return syllabi.findByLmsCourseId(lmsCourseId);
    }

    @Transactional
    public LmsSyllabus saveDraft(Long userAccountId, Long lmsCourseId, String content) {
        LmsCourse lmsCourse = lockedCourse(userAccountId, lmsCourseId);
        Optional<LmsSyllabus> current = syllabi.findByLmsCourseIdForUpdate(lmsCourseId);
        if (current.isPresent()) {
            current.get().saveDraft(content, clock.instant());
            return current.get();
        }
        return syllabi.save(new LmsSyllabus(lmsCourse, content, clock.instant()));
    }

    @Transactional
    public LmsSyllabus publish(Long userAccountId, Long lmsCourseId) {
        lockedCourse(userAccountId, lmsCourseId);
        LmsSyllabus syllabus = syllabi.findByLmsCourseIdForUpdate(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOTHING_TO_PUBLISH));
        syllabus.publish(clock.instant());
        return syllabus;
    }

    private LmsCourse lockedCourse(Long userAccountId, Long lmsCourseId) {
        requireFaculty(userAccountId, lmsCourseId);
        return lmsCourses.findByIdForUpdate(lmsCourseId).orElseThrow(LmsSyllabusService::unavailable);
    }

    private void requireFaculty(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
    }
}
