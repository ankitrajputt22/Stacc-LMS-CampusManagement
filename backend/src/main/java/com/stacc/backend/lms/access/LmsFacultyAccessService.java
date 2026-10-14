package com.stacc.backend.lms.access;

import java.util.Optional;

import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers one question: may the faculty member behind this account use this LMS course right now?
 *
 * <p>The answer comes only from official teaching assignments, starting from the signed-in
 * account. There is no separate LMS membership for faculty. It is a plain yes or no, so a caller
 * cannot learn why access was refused, and checking never creates or changes anything.
 */
@Service
public class LmsFacultyAccessService {

    private final LmsCourseRepository lmsCourses;
    private final TeachingAssignmentRepository teachingAssignments;

    public LmsFacultyAccessService(LmsCourseRepository lmsCourses, TeachingAssignmentRepository teachingAssignments) {
        this.lmsCourses = lmsCourses;
        this.teachingAssignments = teachingAssignments;
    }

    /**
     * True only when the account has an active faculty profile with an active teaching assignment
     * for the LMS course's course offering, and the LMS course is a draft or published. Anything
     * else, including an unknown account or course, is false. The rule itself is stated once, in
     * {@link LmsFacultyAccessPolicy}.
     *
     * @param userAccountId the ID of the signed-in account, taken from its access token and
     *                      never from anything the client sends in the request
     */
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
