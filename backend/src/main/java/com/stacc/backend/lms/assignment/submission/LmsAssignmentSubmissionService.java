package com.stacc.backend.lms.assignment.submission;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsMaterialFiles;
import com.stacc.backend.lms.content.LmsMaterialProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class LmsAssignmentSubmissionService {

    static final String ASSIGNMENT_UNAVAILABLE = "Assignment not found or unavailable.";
    static final String ATTACHMENT_UNAVAILABLE = "File not found or unavailable.";
    static final String NO_CONTENT = "Add a text response or at least one file.";
    static final String TEXT_TOO_LONG = "The text response must be at most 20,000 characters.";
    static final String STORAGE_UNAVAILABLE = "File storage is not available right now. Try again later.";
    static final String UNREADABLE = "The file could not be read.";
    static final String NOT_OPEN = "This assignment does not take work yet.";
    static final String CLOSED = "This assignment no longer takes work.";
    static final String NO_ATTEMPTS_LEFT = "You have used every attempt this assignment allows.";

    private static final Logger LOGGER = LoggerFactory.getLogger(LmsAssignmentSubmissionService.class);

    private final LmsStudentAccessService studentAccess;
    private final LmsAssignmentRepository assignments;
    private final CourseEnrollmentRepository courseEnrollments;
    private final LmsAssignmentSubmissionRepository submissions;
    private final LmsSubmissionAttemptRepository attempts;
    private final LmsSubmissionAttachmentRepository attachments;
    private final LmsSubmissionGradeRepository grades;
    private final ObjectStorageService storage;
    private final Clock clock;
    private final long maxFileBytes;
    private final int maxFiles;

    public LmsAssignmentSubmissionService(
            LmsStudentAccessService studentAccess,
            LmsAssignmentRepository assignments,
            CourseEnrollmentRepository courseEnrollments,
            LmsAssignmentSubmissionRepository submissions,
            LmsSubmissionAttemptRepository attempts,
            LmsSubmissionAttachmentRepository attachments,
            LmsSubmissionGradeRepository grades,
            ObjectStorageService storage,
            Clock clock,
            LmsMaterialProperties materialProperties,
            LmsSubmissionProperties submissionProperties) {
        this.studentAccess = studentAccess;
        this.assignments = assignments;
        this.courseEnrollments = courseEnrollments;
        this.submissions = submissions;
        this.attempts = attempts;
        this.attachments = attachments;
        this.grades = grades;
        this.storage = storage;
        this.clock = clock;
        this.maxFileBytes = materialProperties.maxFileSize().toBytes();
        this.maxFiles = submissionProperties.maxFiles();
    }

    public record SubmittedAttempt(
            LmsSubmissionAttempt attempt, List<LmsSubmissionAttachment> attachments, LmsSubmissionGrade grade) {
    }

    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    private record CheckedUpload(MultipartFile file, LmsMaterialFiles.CheckedFile checked) {
    }

    public SubmittedAttempt submit(
            Long userAccountId, Long lmsCourseId, Long assignmentId, String textContent, List<MultipartFile> files) {
        CourseEnrollment enrollment = studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(() -> notFound(ASSIGNMENT_UNAVAILABLE));
        LmsAssignment assignment = visibleAssignment(lmsCourseId, assignmentId);

        requireOpen(assignment, clock.instant());
        String text = validText(textContent);
        List<MultipartFile> chosen = chosenFiles(files);
        if (chosen.size() > maxFiles) {
            throw badRequest("Attach at most " + maxFiles + (maxFiles == 1 ? " file." : " files."));
        }
        if (text == null && chosen.isEmpty()) {
            throw badRequest(NO_CONTENT);
        }
        List<CheckedUpload> checked = chosen.stream().map(this::checked).toList();

        Long courseEnrollmentId = enrollment.getId();
        List<String> storedKeys = store(assignment.getId(), courseEnrollmentId, checked);
        try {
            courseEnrollments.findByIdForUpdate(courseEnrollmentId)
                    .filter(locked -> locked.getStatus() == CourseEnrollmentStatus.ENROLLED)
                    .orElseThrow(() -> notFound(ASSIGNMENT_UNAVAILABLE));
            LmsAssignmentSubmission submission = submissions.findForUpdate(assignment.getId(), courseEnrollmentId)
                    .orElseGet(() -> submissions.saveAndFlush(
                            new LmsAssignmentSubmission(assignment, enrollment)));
            if (assignment.getMaxAttempts() != null && submission.getLatestAttemptNumber() >= assignment.getMaxAttempts()) {
                throw new ApiException(HttpStatus.CONFLICT, NO_ATTEMPTS_LEFT);
            }
            Instant acceptedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            requireOpen(assignment, acceptedAt);
            int attemptNumber = submission.takeNextAttemptNumber();
            LmsSubmissionAttempt attempt = attempts.saveAndFlush(new LmsSubmissionAttempt(submission, attemptNumber, text,
                    acceptedAt, assignment.getDueAt()));

            List<LmsSubmissionAttachment> saved = new ArrayList<>();
            for (int index = 0; index < checked.size(); index++) {
                LmsMaterialFiles.CheckedFile file = checked.get(index).checked();
                saved.add(new LmsSubmissionAttachment(attempt, storedKeys.get(index), file.fileName(),
                        file.type().contentType(), file.sizeBytes()));
            }
            List<LmsSubmissionAttachment> savedAttachments = attachments.saveAllAndFlush(saved);
            submissions.saveAndFlush(submission);
            return new SubmittedAttempt(attempt, savedAttachments, null);
        } catch (RuntimeException failure) {
            removeQuietly(storedKeys);
            throw failure;
        }
    }

    @Transactional(readOnly = true)
    public List<SubmittedAttempt> history(Long userAccountId, Long lmsCourseId, Long assignmentId) {
        CourseEnrollment enrollment = studentAccess.findAccessGrantingEnrollment(userAccountId, lmsCourseId)
                .orElseThrow(() -> notFound(ASSIGNMENT_UNAVAILABLE));
        LmsAssignment assignment = visibleAssignment(lmsCourseId, assignmentId);

        return submissions.findByAssignmentIdAndCourseEnrollmentId(assignment.getId(), enrollment.getId())
                .map(this::attemptsOf)
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Download openOwnAttachment(Long userAccountId, Long attachmentId) {
        LmsSubmissionAttachment attachment = attachmentId == null
                ? null
                : attachments.findWithSubmissionById(attachmentId).orElse(null);
        if (attachment == null || !isOwnVisibleAttachment(userAccountId, attachment)) {
            throw notFound(ATTACHMENT_UNAVAILABLE);
        }
        try {
            InputStream content = storage.open(attachment.getObjectKey());
            return new Download(attachment.getOriginalFileName(), attachment.getContentType(), attachment.getSizeBytes(), content);
        } catch (ObjectStorageException failure) {
            LOGGER.warn("A submission file could not be read from storage", failure);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
        }
    }

    private boolean isOwnVisibleAttachment(Long userAccountId, LmsSubmissionAttachment attachment) {
        LmsAssignmentSubmission submission = attachment.getAttempt().getSubmission();
        LmsAssignment assignment = submission.getAssignment();
        return assignment.getStatus() == LmsContentStatus.PUBLISHED
                && studentAccess.findAccessGrantingEnrollment(userAccountId, assignment.getLmsCourse().getId())
                        .map(enrollment -> enrollment.getId().equals(submission.getCourseEnrollment().getId()))
                        .orElse(false);
    }

    private static void requireOpen(LmsAssignment assignment, Instant at) {
        switch (assignment.submissionWindowAt(at)) {
            case NOT_OPEN -> throw new ApiException(HttpStatus.CONFLICT, NOT_OPEN);
            case CLOSED -> throw new ApiException(HttpStatus.CONFLICT, CLOSED);
            default -> { }
        }
    }

    private LmsAssignment visibleAssignment(Long lmsCourseId, Long assignmentId) {
        LmsAssignment assignment = assignmentId == null ? null : assignments.findById(assignmentId).orElse(null);
        if (assignment == null
                || assignment.getStatus() != LmsContentStatus.PUBLISHED
                || !assignment.getLmsCourse().getId().equals(lmsCourseId)) {
            throw notFound(ASSIGNMENT_UNAVAILABLE);
        }
        return assignment;
    }

    private List<SubmittedAttempt> attemptsOf(LmsAssignmentSubmission submission) {
        List<LmsSubmissionAttempt> found = attempts.findAllBySubmissionIdOrderByAttemptNumberDesc(submission.getId());
        if (found.isEmpty()) {
            return List.of();
        }
        List<Long> attemptIds = found.stream().map(LmsSubmissionAttempt::getId).toList();
        Map<Long, List<LmsSubmissionAttachment>> filesByAttempt = attachments.findAllByAttemptIdInOrderByIdAsc(attemptIds)
                .stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getAttempt().getId()));
        Map<Long, LmsSubmissionGrade> gradeByAttempt = grades.findAllByAttemptIdIn(attemptIds).stream()
                .collect(Collectors.toMap(grade -> grade.getAttempt().getId(), Function.identity()));
        return found.stream()
                .map(attempt -> new SubmittedAttempt(attempt, filesByAttempt.getOrDefault(attempt.getId(), List.of()),
                        gradeByAttempt.get(attempt.getId())))
                .toList();
    }

    private static String validText(String textContent) {
        if (textContent != null && textContent.length() > LmsSubmissionAttempt.MAX_TEXT_LENGTH) {
            throw badRequest(TEXT_TOO_LONG);
        }
        return LmsSubmissionAttempt.textOrNull(textContent);
    }

    private static List<MultipartFile> chosenFiles(Collection<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null
                        && !(file.isEmpty() && (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())))
                .toList();
    }

    private CheckedUpload checked(MultipartFile file) {
        try {
            return new CheckedUpload(file, LmsMaterialFiles.check(file, maxFileBytes));
        } catch (ApiException refused) {
            throw new ApiException(refused.getStatus(), "“" + label(file) + "”: " + refused.getMessage());
        }
    }

    private static String label(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).replaceAll("\\p{Cntrl}", "").strip();
        if (name.isEmpty()) {
            return "File";
        }
        return name.length() > 100 ? name.substring(0, 100) + "…" : name;
    }

    private List<String> store(Long assignmentId, Long courseEnrollmentId, List<CheckedUpload> uploads) {
        List<String> storedKeys = new ArrayList<>();
        for (CheckedUpload upload : uploads) {
            String key = "lms/submissions/assignments/%d/enrollments/%d/%s"
                    .formatted(assignmentId, courseEnrollmentId, UUID.randomUUID());
            storedKeys.add(key);
            try (InputStream content = upload.file().getInputStream()) {
                storage.store(key, content, upload.checked().sizeBytes(), upload.checked().type().contentType());
            } catch (IOException failure) {
                removeQuietly(storedKeys);
                throw badRequest("“" + label(upload.file()) + "”: " + UNREADABLE);
            } catch (ObjectStorageException failure) {
                LOGGER.warn("A submission file could not be stored", failure);
                removeQuietly(storedKeys);
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, STORAGE_UNAVAILABLE);
            }
        }
        return storedKeys;
    }

    private void removeQuietly(List<String> objectKeys) {
        for (String objectKey : objectKeys) {
            try {
                storage.delete(objectKey);
            } catch (RuntimeException failure) {
                LOGGER.warn("A stored submission file could not be removed after its attempt failed", failure);
            }
        }
    }

    private static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, message);
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
