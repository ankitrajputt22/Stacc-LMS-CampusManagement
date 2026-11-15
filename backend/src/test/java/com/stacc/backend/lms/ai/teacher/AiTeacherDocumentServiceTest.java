package com.stacc.backend.lms.ai.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Teacher-only documents are managed only by faculty who teach the course, and archived rather than deleted. */
class AiTeacherDocumentServiceTest {

    private static final long FACULTY = 5L;
    private static final long OTHER = 6L;
    private static final long COURSE = 3L;

    private final AiTeacherDocumentRepository documents = mock(AiTeacherDocumentRepository.class);
    private final LmsCourseRepository lmsCourses = mock(LmsCourseRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final LmsFacultyAccessService access = mock(LmsFacultyAccessService.class);
    private final AiTeacherDocumentService service = new AiTeacherDocumentService(documents, lmsCourses, accounts, access);
    private final LmsCourse course = ContentFixtures.lmsCourse(COURSE);
    private AiTeacherDocument stored;

    @BeforeEach
    void setUp() {
        when(access.canAccessCourse(FACULTY, COURSE)).thenReturn(true);
        when(lmsCourses.getReferenceById(COURSE)).thenReturn(course);
        when(lmsCourses.findByIdForUpdate(COURSE)).thenReturn(Optional.of(course));
        when(accounts.getReferenceById(FACULTY)).thenReturn(mock(UserAccount.class));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        stored = new AiTeacherDocument(course, mock(UserAccount.class), "Exam focus", "Normal forms matter.");
        when(documents.findWithCourseById(9L)).thenReturn(Optional.of(stored));
    }

    @Test
    void facultyWhoTeachTheCourseAddEditAndArchive() {
        AiTeacherDocument created = service.create(FACULTY, COURSE, " Lab plan ", "Week 3: joins.");
        assertThat(created.getTitle()).isEqualTo("Lab plan");
        assertThat(created.getStatus()).isEqualTo(AiTeacherDocument.Status.ACTIVE);

        service.update(FACULTY, 9L, "Exam focus", "Third normal form.");
        assertThat(stored.getContent()).isEqualTo("Third normal form.");

        service.archive(FACULTY, 9L);
        service.archive(FACULTY, 9L);
        assertThat(stored.isArchived()).isTrue();
        assertRefused(HttpStatus.CONFLICT, () -> service.update(FACULTY, 9L, "Again", "Text"));
        verify(documents, never()).delete(any());
    }

    @Test
    void othersGetTheSame404() {
        assertRefused(HttpStatus.NOT_FOUND, () -> service.list(OTHER, COURSE));
        assertRefused(HttpStatus.NOT_FOUND, () -> service.create(OTHER, COURSE, "Notes", "Text"));
        assertRefused(HttpStatus.NOT_FOUND, () -> service.update(OTHER, 9L, "Notes", "Text"));
        assertRefused(HttpStatus.NOT_FOUND, () -> service.archive(FACULTY, 404L));
        verify(documents, never()).save(any());
    }

    @Test
    void aCourseHoldsAtMostFiftyActiveDocuments() {
        when(documents.countByLmsCourseIdAndStatus(COURSE, AiTeacherDocument.Status.ACTIVE)).thenReturn(50L);
        assertRefused(HttpStatus.CONFLICT, () -> service.create(FACULTY, COURSE, "One more", "Text"));
    }

    @Test
    void knowledgeFingerprintFollowsTheActiveDocuments() {
        when(documents.findByLmsCourseIdAndStatusOrderByIdAsc(COURSE, AiTeacherDocument.Status.ACTIVE)).thenReturn(List.of());
        AiTeacherKnowledge empty = service.knowledge(COURSE);
        assertThat(empty.isEmpty()).isTrue();
        AiTeacherKnowledge one = AiTeacherKnowledge.of(List.of(new AiTeacherKnowledge.Document(9L, "A", "Text")));
        AiTeacherKnowledge edited = AiTeacherKnowledge.of(List.of(new AiTeacherKnowledge.Document(9L, "A", "Text 2")));
        assertThat(one.fingerprint()).matches("[0-9a-f]{64}").isNotEqualTo(edited.fingerprint()).isNotEqualTo(empty.fingerprint());
        assertThat(one.allowedSources()).extracting(source -> source.sourceType() + ":" + source.sourceId())
                .containsExactly("TEACHER_DOCUMENT:9");
        assertThat(one.indexSources().get(0).topicId()).isNull();
    }

    private static void assertRefused(HttpStatus status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getStatus()).isEqualTo(status));
    }
}
