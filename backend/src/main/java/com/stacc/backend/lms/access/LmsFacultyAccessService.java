package com.stacc.backend.lms.access;

import java.util.Optional;

import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LmsFacultyAccessService {

    private final LmsCourseRepository lmsCourses;
    private final TeachingAssignmentRepository teachingAssignments;

    public LmsFacultyAccessService(LmsCourseRepository lmsCourses, TeachingAssignmentRepository teachingAssignments) {
        this.lmsCourses = lmsCourses;
        this.teachingAssignments = teachingAssignments;
    }

    @Transactional(readOnly = true)
    public boolean canAccessCourse(Long userAccountId, Long lmsCourseId) {
        if (userAccountId == null || lmsCourseId == null) {
            return false;
        }

        Optional<LmsCourse> lmsCourse = lmsCourses.findById(lmsCourseId);
        if (lmsCourse.isEmpty() || !LmsFacultyAccessPolicy.isOpenToFaculty(lmsCourse.get().getStatus())) {
            return false;
        }

        Long courseOfferingId = lmsCourse.get().getCourseOffering().getId();
        return teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(userAccountId, courseOfferingId).stream()
                .anyMatch(assignment -> LmsFacultyAccessPolicy.grantsCurrentAccess(assignment, lmsCourse.get(), userAccountId));
    }
}
