package com.stacc.backend.lms.discussion;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LmsCourseParticipation {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";

    public record Viewer(Long accountId, AuthorKind kind) {
    }

    private final LmsStudentAccessService studentAccess;
    private final LmsFacultyAccessService facultyAccess;

    public LmsCourseParticipation(LmsStudentAccessService studentAccess, LmsFacultyAccessService facultyAccess) {
        this.studentAccess = studentAccess;
        this.facultyAccess = facultyAccess;
    }

    public void require(Viewer viewer, Long lmsCourseId) {
        boolean allowed = viewer != null && viewer.accountId() != null && lmsCourseId != null && switch (viewer.kind()) {
            case STUDENT -> studentAccess.canAccessCourse(viewer.accountId(), lmsCourseId);
            case FACULTY -> facultyAccess.canAccessCourse(viewer.accountId(), lmsCourseId);
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }
}
