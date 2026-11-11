package com.stacc.backend.todo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseQueryService;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.mentoring.MentoringService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;

class TodoServiceTest {

    private final LmsStudentCourseQueryService studentCourses = mock(LmsStudentCourseQueryService.class);
    private final LmsFacultyManagedCourseQueryService facultyCourses = mock(LmsFacultyManagedCourseQueryService.class);
    private final TodoDismissalRepository dismissals = mock(TodoDismissalRepository.class);
    private final ApprovalQueryService approvals = mock(ApprovalQueryService.class);
    private final TodoService service = new TodoService(studentCourses, facultyCourses, mock(TodoAssignmentQueries.class),
            mock(AttendanceSessionRepository.class), approvals, dismissals, mock(MentoringService.class),
            Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void eachPartNeedsItsOwnRoleAndPermission() {
        when(dismissals.findKeysByAccountId(1L)).thenReturn(Set.of());
        List<TodoService.TodoItem> items = service.list(1L, AuthorityUtils.createAuthorityList("ROLE_STUDENT", "TODO_VIEW"));
        assertThat(items).isEmpty();
        verifyNoInteractions(studentCourses, facultyCourses, approvals);
    }

    @Test
    void aStudentWithCourseAccessIsAskedAboutTheirCoursesOnly() {
        when(dismissals.findKeysByAccountId(1L)).thenReturn(Set.of());
        when(studentCourses.getAccessibleCourses(1L)).thenReturn(List.of());
        service.list(1L, AuthorityUtils.createAuthorityList("ROLE_STUDENT", "LMS_COURSE_VIEW", "TODO_VIEW"));
        verify(studentCourses).getAccessibleCourses(1L);
        verifyNoInteractions(facultyCourses, approvals);
    }

    @Test
    void onlyWellFormedKeysCanBeDismissed() {
        assertThatThrownBy(() -> service.dismiss(1L, "<script>")).isInstanceOf(ApiException.class);
        verify(dismissals, never()).saveAndFlush(any());
        when(dismissals.existsByUserAccountIdAndItemKey(anyLong(), any())).thenReturn(false);
        service.dismiss(1L, "GRADING:12:3");
        verify(dismissals).saveAndFlush(any());
    }
}
