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

    @Transactional(readOnly = true)
    public Map<Long, List<LmsAnnouncementAttachment>> listedFor(Collection<Long> announcementIds) {
        if (announcementIds.isEmpty()) {
            return Map.of();
        }
        return attachments.findListedFor(announcementIds).stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getAnnouncement().getId()));
    }

    @Transactional
    public LmsAnnouncement add(Long userAccountId, Long announcementId, Collection<MultipartFile> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, NO_FILES);
        }
        LmsAnnouncement announcement = lockedChangeable(userAccountId, announcementId);
        if (attachments.countListed(announcementId) + uploads.size() > MAX_FILES) {
            throw new ApiException(HttpStatus.CONFLICT, TOO_MANY);
        }
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

    @Transactional
    public LmsAnnouncement remove(Long userAccountId, Long announcementId, Long attachmentId) {
        LmsAnnouncement announcement = lockedChangeable(userAccountId, announcementId);
        attachmentOf(announcementId, attachmentId).remove(clock.instant());
        return announcement;
    }

    @Transactional(readOnly = true)
    public LmsAttachmentStore.Download openForFaculty(Long userAccountId, Long announcementId, Long attachmentId) {
        Long lmsCourseId = announcementId == null ? null : announcements.findLmsCourseIdById(announcementId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, FILE_UNAVAILABLE);
        }
        return open(attachmentOf(announcementId, attachmentId));
    }

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
