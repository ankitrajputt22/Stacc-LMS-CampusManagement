package com.stacc.backend.academic.teaching;

import java.util.Optional;

import com.stacc.backend.identity.faculty.FacultyProfileStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacultyTeachingAccessService {

    private final TeachingAssignmentRepository teachingAssignments;

    public FacultyTeachingAccessService(TeachingAssignmentRepository teachingAssignments) {
        this.teachingAssignments = teachingAssignments;
    }

    public static boolean isCurrentTeaching(TeachingAssignment assignment, Long userAccountId) {
        return userAccountId != null
                && userAccountId.equals(assignment.getFacultyProfile().getUserAccount().getId())
                && assignment.getFacultyProfile().getStatus() == FacultyProfileStatus.ACTIVE
                && assignment.getStatus() == TeachingAssignmentStatus.ACTIVE;
    }

    @Transactional(readOnly = true)
    public boolean canTeachCourseOffering(Long userAccountId, Long courseOfferingId) {
        return findCurrentTeachingAssignment(userAccountId, courseOfferingId).isPresent();
    }

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
