package com.stacc.backend.lms.ai.teacher;

import java.util.List;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AiTeacherDocumentService {

    public static final String DOCUMENT_UNAVAILABLE = "Teacher document not found or unavailable.";
    public static final String DOCUMENT_ARCHIVED = "This teacher document is archived and can no longer be changed.";
    static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    static final int MAX_ACTIVE_DOCUMENTS = 50;
    static final String TOO_MANY = "A course can have at most 50 teacher documents. Archive one first.";

    private final AiTeacherDocumentRepository documents;
    private final LmsCourseRepository lmsCourses;
    private final UserAccountRepository accounts;
    private final LmsFacultyAccessService facultyAccess;

    public AiTeacherDocumentService(AiTeacherDocumentRepository documents, LmsCourseRepository lmsCourses,
            UserAccountRepository accounts, LmsFacultyAccessService facultyAccess) {
        this.documents = documents;
        this.lmsCourses = lmsCourses;
        this.accounts = accounts;
        this.facultyAccess = facultyAccess;
    }

    @Transactional(readOnly = true)
    public List<AiTeacherDocument> list(Long accountId, Long lmsCourseId) {
        requireCourse(accountId, lmsCourseId);
        return documents.findByLmsCourseIdAndStatusOrderByIdAsc(lmsCourseId, AiTeacherDocument.Status.ACTIVE);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiTeacherDocument create(Long accountId, Long lmsCourseId, String title, String content) {
        requireCourse(accountId, lmsCourseId);
        lmsCourses.findByIdForUpdate(lmsCourseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        if (documents.countByLmsCourseIdAndStatus(lmsCourseId, AiTeacherDocument.Status.ACTIVE) >= MAX_ACTIVE_DOCUMENTS) {
            throw new ApiException(HttpStatus.CONFLICT, TOO_MANY);
        }
        return documents.save(new AiTeacherDocument(lmsCourses.getReferenceById(lmsCourseId),
                accounts.getReferenceById(accountId), title, content));
    }

    public AiTeacherDocument update(Long accountId, Long documentId, String title, String content) {
        AiTeacherDocument document = manageable(accountId, documentId);
        if (document.isArchived()) {
            throw new ApiException(HttpStatus.CONFLICT, DOCUMENT_ARCHIVED);
        }
        document.edit(title, content);
        return document;
    }

    public AiTeacherDocument archive(Long accountId, Long documentId) {
        AiTeacherDocument document = manageable(accountId, documentId);
        document.archive();
        return document;
    }

    @Transactional(readOnly = true)
    public AiTeacherKnowledge knowledge(Long lmsCourseId) {
        return AiTeacherKnowledge.of(documents.findByLmsCourseIdAndStatusOrderByIdAsc(lmsCourseId,
                AiTeacherDocument.Status.ACTIVE).stream()
                .map(document -> new AiTeacherKnowledge.Document(document.getId(), document.getTitle(), document.getContent()))
                .toList());
    }

    private void requireCourse(Long accountId, Long lmsCourseId) {
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(accountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }

    private AiTeacherDocument manageable(Long accountId, Long documentId) {
        AiTeacherDocument document = documentId == null ? null : documents.findWithCourseById(documentId).orElse(null);
        if (document == null || !facultyAccess.canAccessCourse(accountId, document.getLmsCourse().getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, DOCUMENT_UNAVAILABLE);
        }
        return document;
    }
}
