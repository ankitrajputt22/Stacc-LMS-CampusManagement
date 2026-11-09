package com.stacc.backend.academic.teaching;

import java.util.Optional;

import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers one academic question: does the faculty member behind this account teach this course offering right
 * now? The answer comes only from official teaching assignments, starting from the signed-in account. It needs
 * no LMS course, so academic features such as attendance work whether or not one exists. Checking never creates
 * or changes anything.
 */
@Service
public class FacultyTeachingAccessService {

    private final TeachingAssignmentRepository teachingAssignments;

    public FacultyTeachingAccessService(TeachingAssignmentRepository teachingAssignments) {
        this.teachingAssignments = teachingAssignments;
    }

    /**
     * The one rule: the teaching assignment belongs to the faculty member behind this account, their faculty
     * profile is active, and the assignment itself is active. Lists and single checks both use it, so they
     * cannot disagree.
     */
    public static boolean isCurrentTeaching(TeachingAssignment assignment, Long userAccountId) {
        return userAccountId != null
                && userAccountId.equals(assignment.getFacultyProfile().getUserAccount().getId())
                && assignment.getFacultyProfile().getStatus() == FacultyProfileStatus.ACTIVE
                && assignment.getStatus() == TeachingAssignmentStatus.ACTIVE;
    }

    /**
     * True only when the account has an active faculty profile with an active teaching assignment for the
     * course offering. Anything else, including an unknown account or offering, is false.
     *
     * @param userAccountId the ID of the signed-in account, taken from its access token and never from
     *                      anything the client sends in the request
     */
    @Transactional(readOnly = true)
    public boolean canTeachCourseOffering(Long userAccountId, Long courseOfferingId) {
        return findCurrentTeachingAssignment(userAccountId, courseOfferingId).isPresent();
    }

    /**
     * The teaching assignment that lets this account teach the course offering now, with its faculty profile,
     * or empty. Callers that act on the offering use its faculty profile as the one acting.
     */
    @Transactional(readOnly = true)
    public Optional<TeachingAssignment> findCurrentTeachingAssignment(Long userAccountId, Long courseOfferingId) {
        if (userAccountId == null || courseOfferingId == null) {
            return Optional.empty();
        }
        return teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(userAccountId, courseOfferingId).stream()
                .filter(assignment -> isCurrentTeaching(assignment, userAccountId))
                .findFirst();
    }
}
