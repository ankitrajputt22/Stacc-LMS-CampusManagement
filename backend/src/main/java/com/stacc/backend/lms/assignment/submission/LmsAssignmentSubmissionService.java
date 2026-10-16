package com.stacc.backend.lms.assignment.submission;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.common.storage.ObjectStorageException;
import com.stacc.backend.common.storage.ObjectStorageService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.LmsAssignmentRepository;
import com.stacc.backend.lms.content.LmsContentStatus;
import com.stacc.backend.lms.content.LmsMaterialFiles;
import com.stacc.backend.lms.content.LmsMaterialProperties;
import com.stacc.backend.lms.membership.LmsStudentMembership;
import com.stacc.backend.lms.membership.LmsStudentMembershipRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Students hand in work for published assignments, read back their own attempts, and download their own
 * files. The student is always the signed-in account, and their course enrollment is found on the server,
 * through {@link LmsStudentAccessService}: a request can never say whose submission it is.
 *
 * <p>Every hand-in is a new attempt. Earlier attempts are never changed or removed. Late work is accepted
 * and marked late against the deadline that applied at that moment. Files are checked with the same rules
 * as study materials and kept in private object storage; only their details are in the database.
 */
@Service
@Transactional
public class LmsAssignmentSubmissionService {

    static final String ASSIGNMENT_UNAVAILABLE = "Assignment not found or unavailable.";
    static final String ATTACHMENT_UNAVAILABLE = "File not found or unavailable.";
    static final String NO_CONTENT = "Add a text response or at least one file.";
    static final String TEXT_TOO_LONG = "The text response must be at most 20,000 characters.";
    static final String STORAGE_UNAVAILABLE = "File storage is not available right now. Try again later.";
    static final String UNREADABLE = "The file could not be read.";

    private static final Logger LOGGER = LoggerFactory.getLogger(LmsAssignmentSubmissionService.class);

    private final LmsStudentAccessService studentAccess;
    private final LmsAssignmentRepository assignments;
    private final LmsStudentMembershipRepository memberships;
    private final LmsAssignmentSubmissionRepository submissions;
    private final LmsSubmissionAttemptRepository attempts;
    private final LmsSubmissionAttachmentRepository attachments;
    private final ObjectStorageService storage;
    private final Clock clock;
    private final long maxFileBytes;
    private final int maxFiles;

    public LmsAssignmentSubmissionService(
            LmsStudentAccessService studentAccess,
            LmsAssignmentRepository assignments,
            LmsStudentMembershipRepository memberships,
            LmsAssignmentSubmissionRepository submissions,
            LmsSubmissionAttemptRepository attempts,
            LmsSubmissionAttachmentRepository attachments,
            ObjectStorageService storage,
            Clock clock,
            LmsMaterialProperties materialProperties,
            LmsSubmissionProperties submissionProperties) {
        this.studentAccess = studentAccess;
        this.assignments = assignments;
        this.memberships = memberships;
        this.submissions = submissions;
        this.attempts = attempts;
        this.attachments = attachments;
        this.storage = storage;
        this.clock = clock;
        this.maxFileBytes = materialProperties.maxFileSize().toBytes();
        this.maxFiles = submissionProperties.maxFiles();
    }

    /** One attempt with its files, in the order they were sent. */
    public record SubmittedAttempt(LmsSubmissionAttempt attempt, List<LmsSubmissionAttachment> attachments) {
    }

    /** An opened file for a download. The caller must close the content stream. */
    public record Download(String fileName, String contentType, long sizeBytes, InputStream content) {
    }

    private record CheckedUpload(MultipartFile file, LmsMaterialFiles.CheckedFile checked) {
    }

    /**
     * Makes a new attempt for the student behind this account. The assignment must be published and in a
     * course the student may use now; otherwise the answer is the same 404 as for one that does not exist.
     * The attempt needs non-blank text, at least one file, or both.
     *
     * <p>Files are checked first and stored before anything is written to the database, so no lock is held
     * while they upload. The attempt number is then reserved under a lock on the student's membership in
     * the course, and the submission record is read with a lock too, so two requests from the same student
     * never get the same number; a unique constraint backs this up. The database and the storage cannot
     * share a transaction: if a file cannot be stored, or the attempt cannot be saved, the files already
     * stored for it are removed again.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    public SubmittedAttempt submit(
            Long userAccountId, Long lmsCourseId, Long assignmentId, String textContent, List<MultipartFile> files) {
        LmsStudentMembership membership = studentAccess.findAccessGrantingMembership(userAccountId, lmsCourseId)
                .orElseThrow(() -> notFound(ASSIGNMENT_UNAVAILABLE));
        LmsAssignment assignment = visibleAssignment(lmsCourseId, assignmentId);

        String text = validText(textContent);
        List<MultipartFile> chosen = chosenFiles(files);
        if (chosen.size() > maxFiles) {
            throw badRequest("Attach at most " + maxFiles + (maxFiles == 1 ? " file." : " files."));
        }
        if (text == null && chosen.isEmpty()) {
            throw badRequest(NO_CONTENT);
        }
        List<CheckedUpload> checked = chosen.stream().map(this::checked).toList();

        Long courseEnrollmentId = membership.getCourseEnrollment().getId();
        List<String> storedKeys = store(assignment.getId(), courseEnrollmentId, checked);
        try {
            memberships.findByIdForUpdate(membership.getId()).orElseThrow(() -> notFound(ASSIGNMENT_UNAVAILABLE));
            LmsAssignmentSubmission submission = submissions.findForUpdate(assignment.getId(), courseEnrollmentId)
                    .orElseGet(() -> submissions.saveAndFlush(
                            new LmsAssignmentSubmission(assignment, membership.getCourseEnrollment())));
            int attemptNumber = submission.takeNextAttemptNumber();
            // Accepted now, by the server's clock, at the precision the database keeps, so the answer shows
            // exactly what every later read shows. The deadline that applies now is kept with the attempt.
            LmsSubmissionAttempt attempt = attempts.saveAndFlush(new LmsSubmissionAttempt(submission, attemptNumber, text,
                    clock.instant().truncatedTo(ChronoUnit.MICROS), assignment.getDueAt()));

            List<LmsSubmissionAttachment> saved = new ArrayList<>();
            for (int index = 0; index < checked.size(); index++) {
                LmsMaterialFiles.CheckedFile file = checked.get(index).checked();
                saved.add(new LmsSubmissionAttachment(attempt, storedKeys.get(index), file.fileName(),
                        file.type().contentType(), file.sizeBytes()));
            }
            List<LmsSubmissionAttachment> savedAttachments = attachments.saveAllAndFlush(saved);
            submissions.saveAndFlush(submission);
            return new SubmittedAttempt(attempt, savedAttachments);
        } catch (RuntimeException failure) {
            removeQuietly(storedKeys);
            throw failure;
        }
    }

    /**
     * The student's own attempts for an assignment they may see now, newest first, each with its files.
     * A student who has not handed anything in gets an empty list, not an error.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
    @Transactional(readOnly = true)
    public List<SubmittedAttempt> history(Long userAccountId, Long lmsCourseId, Long assignmentId) {
        LmsStudentMembership membership = studentAccess.findAccessGrantingMembership(userAccountId, lmsCourseId)
                .orElseThrow(() -> notFound(ASSIGNMENT_UNAVAILABLE));
        LmsAssignment assignment = visibleAssignment(lmsCourseId, assignmentId);

        return submissions.findByAssignmentIdAndCourseEnrollmentId(assignment.getId(), membership.getCourseEnrollment().getId())
                .map(this::attemptsOf)
                .orElse(List.of());
    }

    /**
     * Opens one of the student's own files for download. The file must belong to an attempt made through
     * the course enrollment that gives this account access to the course now, for an assignment that is
     * still published. Anything else, including another student's file, gets the same 404 as a file that
     * does not exist.
     *
     * @param userAccountId the ID of the signed-in account, never an ID sent by the client
     */
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
                && studentAccess.findAccessGrantingMembership(userAccountId, assignment.getLmsCourse().getId())
                        .map(membership -> membership.getCourseEnrollment().getId().equals(submission.getCourseEnrollment().getId()))
                        .orElse(false);
    }

    // The assignment must be published and belong to the course in the request. The course is checked from
    // the stored assignment, so an assignment of another course is unavailable here.
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
        Map<Long, List<LmsSubmissionAttachment>> filesByAttempt = attachments
                .findAllByAttemptIdInOrderByIdAsc(found.stream().map(LmsSubmissionAttempt::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getAttempt().getId()));
        return found.stream()
                .map(attempt -> new SubmittedAttempt(attempt, filesByAttempt.getOrDefault(attempt.getId(), List.of())))
                .toList();
    }

    private static String validText(String textContent) {
        if (textContent != null && textContent.length() > LmsSubmissionAttempt.MAX_TEXT_LENGTH) {
            throw badRequest(TEXT_TOO_LONG);
        }
        return LmsSubmissionAttempt.textOrNull(textContent);
    }

    // A form with an empty file field sends a part with no name and no bytes. That is no file at all.
    private static List<MultipartFile> chosenFiles(Collection<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null
                        && !(file.isEmpty() && (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())))
                .toList();
    }

    // The same checks as study materials: size, allowed type, declared type, and first bytes. A refusal
    // names the file, because an attempt can have several.
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

    // Keys are made here. Nothing the client sends, not even a file name, becomes part of one, and no
    // login ID or name is in them. Every file gets its own random part.
    private List<String> store(Long assignmentId, Long courseEnrollmentId, List<CheckedUpload> uploads) {
        List<String> storedKeys = new ArrayList<>();
        for (CheckedUpload upload : uploads) {
            String key = "lms/submissions/assignments/%d/enrollments/%d/%s"
                    .formatted(assignmentId, courseEnrollmentId, UUID.randomUUID());
            // Recorded before storing, so a store that fails half way is cleaned up as well.
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
