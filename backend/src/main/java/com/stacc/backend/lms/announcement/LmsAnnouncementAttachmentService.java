package com.stacc.backend.lms.announcement;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.attachment.LmsAttachmentStore;
import com.stacc.backend.lms.attachment.LmsAttachmentStore.StoredFile;
import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Files on course announcements. Faculty add and remove them under the faculty access policy, finding the course from
 * the stored announcement; students download them only from published announcements of courses the student access
 * policy allows. Uploads follow the shared file rules of {@link LmsAttachmentStore}; a removed file is kept. Anything
 * the caller may not reach answers the same 404 as a missing file.
 */
@Service
public class LmsAnnouncementAttachmentService {

    public static final int MAX_FILES = 10;
    public static final String ANNOUNCEMENT_UNAVAILABLE = "Announcement not found or unavailable.";
    public static final String FILE_UNAVAILABLE = "File not found or unavailable.";
    public static final String ARCHIVED = "This announcement is archived and can no longer be changed.";
    public static final String TOO_MANY = "An announcement can have at most " + MAX_FILES + " files.";
    public static final String NO_FILES = "Choose at least one file.";

    private final LmsAnnouncementRepository announcements;
    private final LmsAnnouncementAttachmentRepository attachments;
    private final LmsAttachmentStore files;
    private final LmsFacultyAccessService facultyAccess;
    private final LmsStudentAccessService studentAccess;
    private final Clock clock;

    public LmsAnnouncementAttachmentService(LmsAnnouncementRepository announcements,
            LmsAnnouncementAttachmentRepository attachments, LmsAttachmentStore files, LmsFacultyAccessService facultyAccess,
            LmsStudentAccessService studentAccess, Clock clock) {
        this.announcements = announcements;
        this.attachments = attachments;
        this.files = files;
        this.facultyAccess = facultyAccess;
        this.studentAccess = studentAccess;
        this.clock = clock;
    }

    /** The files still listed on these announcements, by announcement. For lists the caller was allowed to read. */
    @Transactional(readOnly = true)
    public Map<Long, List<LmsAnnouncementAttachment>> listedFor(Collection<Long> announcementIds) {
        if (announcementIds.isEmpty()) {
            return Map.of();
        }
        return attachments.findListedFor(announcementIds).stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getAnnouncement().getId()));
    }

    /** Adds files to a draft or published announcement, at most {@value #MAX_FILES} listed in all. */
    @Transactional
    public LmsAnnouncement add(Long userAccountId, Long announcementId, Collection<MultipartFile> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, NO_FILES);
        }
        LmsAnnouncement announcement = lockedChangeable(userAccountId, announcementId);
        if (attachments.countListed(announcementId) + uploads.size() > MAX_FILES) {
            throw new ApiException(HttpStatus.CONFLICT, TOO_MANY);
        }
        // Keys are made from IDs only; if recording the files fails, the stored bytes are removed again.
        List<StoredFile> stored = files.storeAll("lms/courses/%d/announcements/%d"
                .formatted(announcement.getLmsCourse().getId(), announcementId), uploads);
        try {
            stored.forEach(file -> attachments.save(new LmsAnnouncementAttachment(announcement, file, clock.instant())));
            attachments.flush();
        } catch (RuntimeException failure) {
            files.removeQuietly(stored);
            throw failure;
        }
        return announcement;
    }

    /** Stops listing a file; its bytes are kept. Removing again changes nothing. */
    @Transactional
    public LmsAnnouncement remove(Long userAccountId, Long announcementId, Long attachmentId) {
        LmsAnnouncement announcement = lockedChangeable(userAccountId, announcementId);
        attachmentOf(announcementId, attachmentId).remove(clock.instant());
        return announcement;
    }

    /** A listed file of an announcement in a course the faculty member teaches. */
    @Transactional(readOnly = true)
    public LmsAttachmentStore.Download openForFaculty(Long userAccountId, Long announcementId, Long attachmentId) {
        Long lmsCourseId = announcementId == null ? null : announcements.findLmsCourseIdById(announcementId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, FILE_UNAVAILABLE);
        }
        return open(attachmentOf(announcementId, attachmentId));
    }

    /** A listed file of a published announcement of a course the student may use now. */
    @Transactional(readOnly = true)
    public LmsAttachmentStore.Download openForStudent(Long userAccountId, Long lmsCourseId, Long announcementId, Long attachmentId) {
        LmsAnnouncement announcement = announcementId == null ? null : announcements.findById(announcementId).orElse(null);
        if (announcement == null || !announcement.getLmsCourse().getId().equals(lmsCourseId)
                || announcement.getStatus() != LmsContentStatus.PUBLISHED
                || !studentAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, FILE_UNAVAILABLE);
        }
        return open(attachmentOf(announcementId, attachmentId));
    }

    private LmsAttachmentStore.Download open(LmsAnnouncementAttachment attachment) {
        return files.open(attachment.getObjectKey(), attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes());
    }

    private LmsAnnouncementAttachment attachmentOf(Long announcementId, Long attachmentId) {
        return (attachmentId == null ? java.util.Optional.<LmsAnnouncementAttachment>empty() : attachments.findById(attachmentId))
                .filter(attachment -> attachment.getAnnouncement().getId().equals(announcementId) && !attachment.isRemoved())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, FILE_UNAVAILABLE));
    }

    // Access from the stored announcement's course, then the row lock; the locking read is the first to load it.
    private LmsAnnouncement lockedChangeable(Long userAccountId, Long announcementId) {
        Long lmsCourseId = announcementId == null ? null : announcements.findLmsCourseIdById(announcementId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ANNOUNCEMENT_UNAVAILABLE);
        }
        LmsAnnouncement announcement = announcements.findByIdForUpdate(announcementId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ANNOUNCEMENT_UNAVAILABLE));
        if (announcement.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, ARCHIVED);
        }
        return announcement;
    }
}
