package com.stacc.backend.lms.ai.saved;

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
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.ai.AiCourseKnowledge;
import com.stacc.backend.lms.ai.AiKnowledgeSources;
import com.stacc.backend.lms.ai.notes.AiStudentNoteKnowledge;
import com.stacc.backend.lms.ai.notes.AiStudentNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiSavedAnswerServiceTest {

    private static final long STUDENT = 11L;
    private static final long COURSE = 7L;

    private final AiSavedAnswerRepository answers = mock(AiSavedAnswerRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final LmsStudentAccessService access = mock(LmsStudentAccessService.class);
    private final AiKnowledgeSources sources = mock(AiKnowledgeSources.class);
    private final AiStudentNoteService notes = mock(AiStudentNoteService.class);
    private final AiSavedAnswerService service = new AiSavedAnswerService(answers, accounts, access, sources, notes);

    @BeforeEach
    void setUp() {
        when(access.canAccessCourse(STUDENT, COURSE)).thenReturn(true);
        when(accounts.findByIdForUpdate(STUDENT)).thenReturn(Optional.of(mock(UserAccount.class)));
        when(answers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sources.publishedKnowledge(COURSE)).thenReturn(new AiCourseKnowledge("c".repeat(64),
                List.of(new AiCourseKnowledge.Topic(1, 10, "Normal forms", "Text")),
                List.of(new AiCourseKnowledge.Material(20, 1, "Notes", "notes.pdf", "application/pdf", 10, "secret/key"))));
        when(notes.knowledge(COURSE, STUDENT)).thenReturn(new AiStudentNoteKnowledge("d".repeat(64),
                List.of(new AiStudentNoteKnowledge.Note(4L, "Mine", "Text"))));
    }

    @Test
    void sourcesAreRebuiltByTheServerAndUnknownOnesDropped() {
        AiSavedAnswer saved = service.save(STUDENT, COURSE, "What is 3NF?", "It removes transitive dependencies.", List.of(
                new AiSavedAnswerService.SourceRef("TOPIC", 1, null, null),
                new AiSavedAnswerService.SourceRef("STUDY_MATERIAL", 20, 4, null),
                new AiSavedAnswerService.SourceRef("NOTE", 4, null, null),
                new AiSavedAnswerService.SourceRef("TOPIC", 999, null, null),
                new AiSavedAnswerService.SourceRef("TEACHER_DOCUMENT", 9, null, null),
                new AiSavedAnswerService.SourceRef("TOPIC", 1, null, null)));
        assertThat(saved.getSources()).extracting(source -> source.getSourceType() + ":" + source.getTitle() + ":" + source.getPageId())
                .containsExactly("TOPIC:Normal forms:1", "STUDY_MATERIAL:Notes:1", "NOTE:Mine:null");
        assertThat(saved.getSources().get(1).getPageNumber()).isEqualTo(4);
        assertThat(saved.getSources().toString()).doesNotContain("secret");
    }

    @Test
    void theQuotaAndAccessHold() {
        when(answers.countByLmsCourseIdAndUserAccountId(COURSE, STUDENT)).thenReturn(50L);
        assertThatThrownBy(() -> service.save(STUDENT, COURSE, "Q?", "A.", List.of()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.list(12L, COURSE))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.delete(STUDENT, COURSE, 3L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(answers, never()).delete(any());
    }
}
