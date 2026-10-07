package com.stacc.backend.lms.access;

import java.util.Optional;

import com.stacc.backend.identity.student.StudentProfile;
import com.stacc.backend.identity.student.StudentProfileRepository;
import com.stacc.backend.identity.student.StudentProfileStatus;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.course.LmsCourseStatus;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers one question: may the student behind this account use this LMS course right now?
 *
 * <p>The answer comes only from stored official data, starting from the signed-in account.
 * It is a plain yes or no, so a caller cannot learn why access was refused. Checking access
 * never creates, repairs, or changes anything: a missing membership stays missing.
 */
@Service
public class LmsStudentAccessService {

    private final StudentProfileRepository studentProfiles;
    private final LmsCourseRepository lmsCourses;
    private final LmsStudentMembershipRepository memberships;

    public LmsStudentAccessService(
            StudentProfileRepository studentProfiles,
            LmsCourseRepository lmsCourses,
            LmsStudentMembershipRepository memberships) {
        this.studentProfiles = studentProfiles;
        this.lmsCourses = lmsCourses;
        this.memberships = memberships;
    }

    /**
     * True only when the account has an active student profile, the LMS course is published,
     * and the student has an active membership in it that rests on a semester enrollment and
     * a course enrollment that are both still enrolled, for the same course offering as the
     * LMS course. Anything else, including an unknown account or course, is false. The rule
     * itself is stated once, in {@link LmsStudentAccessPolicy}.
     *
     * @param userAccountId the ID of the signed-in account, taken from its access token and
     *                      never from anything the client sends in the request
     */
    @Transactional(readOnly = true)
    public boolean canAccessCourse(Long userAccountId, Long lmsCourseId) {
        if (userAccountId == null || lmsCourseId == null) {
            return false;
        }

        Optional<StudentProfile> studentProfile = studentProfiles.findByUserAccountId(userAccountId);
        if (studentProfile.isEmpty() || studentProfile.get().getStatus() != StudentProfileStatus.ACTIVE) {
            return false;
        }

        Optional<LmsCourse> lmsCourse = lmsCourses.findById(lmsCourseId);
        if (lmsCourse.isEmpty() || lmsCourse.get().getStatus() != LmsCourseStatus.PUBLISHED) {
            return false;
        }

        // The lookup already asks for this LMS course. It is checked again here, so that access
        // never depends on the lookup alone.
        return memberships.findAllByUserAccountIdAndLmsCourseId(userAccountId, lmsCourseId).stream()
                .anyMatch(membership -> lmsCourseId.equals(membership.getLmsCourse().getId())
                        && LmsStudentAccessPolicy.grantsCurrentAccess(membership, userAccountId));
    }
}
