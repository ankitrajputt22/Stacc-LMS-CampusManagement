package com.stacc.backend.lms.api;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmission;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionRepository;
import com.stacc.backend.lms.assignment.submission.LmsAssignmentSubmissionService;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttachment;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttachmentRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttemptRepository;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGrade;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionGradeRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only review of students' submissions, for faculty who grade them. Review applies to published
 * assignments in courses the signed-in faculty member may use now, decided by {@link LmsFacultyAccessService}
 * from official teaching assignments. Anything else, including another course's submissions, answers the
 * same 404 as something that does not exist. Students are identified by their account's login ID only.
 */
@Service
@Transactional(readOnly = true)
public class LmsFacultySubmissionQueryService {

    static final String ASSIGNMENT_UNAVAILABLE = "Assignment not found or unavailable.";
    static final String SUBMISSION_UNAVAILABLE = "Submission not found or unavailable.";
    static final String ATTACHMENT_UNAVAILABLE = "File not found or unavailable.";
    static final String STORAGE_UNAVAILABLE = "File storage is not available right now. Try again later.";

    private static final Logger LOGGER = LoggerFactory.getLogger(LmsFacultySubmissionQueryService.class);

    private static final Comparator<LmsFacultySubmissionSummaryResponse> NEWEST_FIRST = Comparator
            .comparing(LmsFacultySubmissionSummaryResponse::latestSubmittedAt, Comparator.reverseOrder())
            .thenComparing(LmsFacultySubmissionSummaryResponse::submissionId);

    private final LmsAssignmentRepository assignments;
    private final LmsAssignmentSubmissionRepository submissions;
    private final LmsSubmissionAttemptRepository attempts;
    private final LmsSubmissionAttachmentRepository attachments;
    private final LmsSubmissionGradeRepository grades;
    private final LmsFacultyAccessService facultyAccess;
    private final ObjectStorageService storage;

    public LmsFacultySubmissionQueryService(
            LmsAssignmentRepository assignments,
            LmsAssignmentSubmissionRepository submissions,
            LmsSubmissionAttemptRepository attempts,
            LmsSubmissionAttachmentRepository attachments,
            LmsSubmissionGradeRepository grades,
            LmsFacultyAccessService facultyAccess,
            ObjectStorageService storage) {
        this.assignments = assignments;
        this.submissions = submissions;
        this.attempts = attempts;
        this.attachments = attachments;
        this.grades = grades;
        this.facultyAccess = facultyAccess;
        this.storage = storage;
    }

    /**
     * Every student who has handed in work for the assignment, newest latest attempt first, then by submission.
     * Each row describes the latest attempt only: an older attempt's grade never makes it look graded. Students
     * who have not handed anything in are not listed. The number of queries does not grow with the students.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public List<LmsFacultySubmissionSummaryResponse> listSubmissions(Long userAccountId, Long assignmentId) {
        LmsAssignment assignment = reviewableAssignment(userAccountId, assignmentId);
        List<LmsAssignmentSubmission> found = submissions.findAllWithStudentByAssignmentId(assignment.getId());
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> submissionIds = found.stream().map(LmsAssignmentSubmission::getId).toList();
        Map<Long, LmsSubmissionAttempt> latestBySubmission = attempts.findLatestBySubmissionIdIn(submissionIds).stream()
                .collect(Collectors.toMap(attempt -> attempt.getSubmission().getId(), Function.identity()));
        Map<Long, Long> countBySubmission = attempts.countBySubmissionIdIn(submissionIds).stream()
                .collect(Collectors.toMap(LmsSubmissionAttemptRepository.AttemptCount::getSubmissionId,
                        LmsSubmissionAttemptRepository.AttemptCount::getAttemptCount));
        Map<Long, LmsSubmissionGrade> gradeByAttempt = gradesOf(
                latestBySubmission.values().stream().map(LmsSubmissionAttempt::getId).toList());

        return found.stream()
                .filter(submission -> latestBySubmission.containsKey(submission.getId()))
                .map(submission -> {
                    LmsSubmissionAttempt latest = latestBySubmission.get(submission.getId());
                    LmsSubmissionGrade grade = gradeByAttempt.get(latest.getId());
                    return new LmsFacultySubmissionSummaryResponse(
                            submission.getId(),
                            studentLoginId(submission),
                            latest.getAttemptNumber(),
                            countBySubmission.getOrDefault(submission.getId(), 0L),
                            latest.getSubmittedAt(),
                            latest.isLate(),
                            grade != null,
                            grade == null ? null : grade.getMarksAwarded(),
                            grade == null ? null : grade.getMaxMarksSnapshot());
                })
                .sorted(NEWEST_FIRST)
                .toList();
    }

    /**
     * One student's submission for the assignment, with every attempt newest first, each with its text, files,
     * the deadline that applied, whether it was late, and its own grade.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public LmsFacultySubmissionDetailResponse submissionDetail(Long userAccountId, Long assignmentId, Long submissionId) {
        LmsAssignment assignment = reviewableAssignment(userAccountId, assignmentId);
        LmsAssignmentSubmission submission = submissionId == null
                ? null
                : submissions.findWithStudentById(submissionId).orElse(null);
        if (submission == null
                || !submission.getAssignment().getId().equals(assignment.getId())
                || submission.getLatestAttemptNumber() < 1) {
            throw notFound(SUBMISSION_UNAVAILABLE);
        }

        List<LmsSubmissionAttempt> found = attempts.findAllBySubmissionIdOrderByAttemptNumberDesc(submission.getId());
        List<Long> attemptIds = found.stream().map(LmsSubmissionAttempt::getId).toList();
        Map<Long, List<LmsSubmissionAttachment>> filesByAttempt = attemptIds.isEmpty()
                ? Map.of()
                : attachments.findAllByAttemptIdInOrderByIdAsc(attemptIds).stream()
                        .collect(Collectors.groupingBy(attachment -> attachment.getAttempt().getId()));
        Map<Long, LmsSubmissionGrade> gradeByAttempt = gradesOf(attemptIds);

        List<LmsFacultySubmissionAttemptResponse> attemptResponses = found.stream()
                .map(attempt -> new LmsFacultySubmissionAttemptResponse(
                        attempt.getId(),
                        attempt.getAttemptNumber(),
                        attempt.getTextContent(),
                        attempt.getSubmittedAt(),
                        attempt.getDueAtSnapshot(),
                        attempt.isLate(),
                        filesByAttempt.getOrDefault(attempt.getId(), List.of()).stream()
                                .map(LmsSubmissionAttachmentResponse::of).toList(),
                        LmsSubmissionGradeResponse.of(gradeByAttempt.get(attempt.getId()))))
                .toList();
        return new LmsFacultySubmissionDetailResponse(assignment.getId(), assignment.getTitle(), assignment.getMaxMarks(),
                assignment.getDueAt(), studentLoginId(submission), submission.getId(), attemptResponses);
    }

    /**
     * Opens a student's submitted file for a faculty member who may review it: the file's assignment is
     * published and in a course the caller teaches now. Anything else gets the same 404 as a file that does
     * not exist. Owning the file as a student does not count here.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public LmsAssignmentSubmissionService.Download openAttachment(Long userAccountId, Long attachmentId) {
        LmsSubmissionAttachment attachment = attachmentId == null
                ? null
                : attachments.findWithSubmissionById(attachmentId).orElse(null);
        if (attachment == null || !isReviewable(userAccountId, attachment.getAttempt().getSubmission().getAssignment())) {
            throw notFound(ATTACHMENT_UNAVAILABLE);
        }
        try {
            InputStream content = storage.open(attachment.getObjectKey());
            return new LmsAssignmentSubmissionService.Download(
                    attachment.getOriginalFileName(), attachment.getContentType(), attachment.getSizeBytes(), content);
        } catch (ObjectStorageException failure) {
            LOGGER.warn("A submission file could not be read from storage for review", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        }
    }

    private LmsAssignment reviewableAssignment(Long userAccountId, Long assignmentId) {
        LmsAssignment assignment = assignmentId == null ? null : assignments.findById(assignmentId).orElse(null);
        if (assignment == null || !isReviewable(userAccountId, assignment)) {
            throw notFound(ASSIGNMENT_UNAVAILABLE);
        }
        return assignment;
    }

    // Review is part of the normal workflow of a published assignment in a course the caller teaches now.
    private boolean isReviewable(Long userAccountId, LmsAssignment assignment) {
        return assignment.getStatus() == LmsContentStatus.PUBLISHED
                && facultyAccess.canAccessCourse(userAccountId, assignment.getLmsCourse().getId());
    }

    private Map<Long, LmsSubmissionGrade> gradesOf(List<Long> attemptIds) {
        if (attemptIds.isEmpty()) {
            return Map.of();
        }
        return grades.findAllByAttemptIdIn(attemptIds).stream()
                .collect(Collectors.toMap(grade -> grade.getAttempt().getId(), Function.identity()));
    }

    private static String studentLoginId(LmsAssignmentSubmission submission) {
        return submission.getCourseEnrollment().getSemesterEnrollment().getStudentProfile().getUserAccount().getLoginId();
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }
}
