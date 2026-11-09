package com.stacc.backend.lms.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.access.LmsStudentAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class LmsMessageServiceTest {

    private final LmsConversationRepository conversations = mock(LmsConversationRepository.class);
    private final LmsConversationMessageRepository messages = mock(LmsConversationMessageRepository.class);
    private final LmsStudentAccessService studentAccess = mock(LmsStudentAccessService.class);
    private final LmsFacultyAccessService facultyAccess = mock(LmsFacultyAccessService.class);
    private final LmsMessageService service = new LmsMessageService(conversations, messages, mock(LmsCourseRepository.class),
            mock(UserAccountRepository.class), new LmsCourseParticipation(studentAccess, facultyAccess),
            mock(LmsMessageAttachmentRepository.class), mock(com.stacc.backend.lms.attachment.LmsAttachmentStore.class),
            Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC));

    private LmsConversation conversationOf(long studentId) {
        LmsCourse course = mock(LmsCourse.class);
        when(course.getId()).thenReturn(5L);
        UserAccount student = mock(UserAccount.class);
        when(student.getId()).thenReturn(studentId);
        LmsConversation conversation = new LmsConversation(course, student, "Question", Instant.parse("2026-11-09T10:00:00Z"));
        ReflectionTestUtils.setField(conversation, "id", 9L);
        when(conversations.findById(9L)).thenReturn(Optional.of(conversation));
        when(messages.findAllForConversation(9L)).thenReturn(List.of());
        return conversation;
    }

    @Test
    void aStudentNeverSeesAnotherStudentsConversation() {
        conversationOf(1L);
        when(studentAccess.canAccessCourse(2L, 5L)).thenReturn(true);
        assertThatThrownBy(() -> service.detail(new Viewer(2L, AuthorKind.STUDENT), 5L, 9L))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void theCoursesFacultyReadEveryConversation() {
        conversationOf(1L);
        when(facultyAccess.canAccessCourse(7L, 5L)).thenReturn(true);
        assertThat(service.detail(new Viewer(7L, AuthorKind.FACULTY), 5L, 9L).conversation().getId()).isEqualTo(9L);
    }

    @Test
    void aStudentWhoLostCourseAccessLosesTheirConversation() {
        conversationOf(1L);
        assertThatThrownBy(() -> service.detail(new Viewer(1L, AuthorKind.STUDENT), 5L, 9L)).isInstanceOf(ApiException.class);
    }
}
