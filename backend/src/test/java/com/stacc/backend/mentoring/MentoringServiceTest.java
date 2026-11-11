package com.stacc.backend.mentoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Only the pair opens a pairing, only the mentor records meetings, and private notes never reach the student. */
class MentoringServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private final MentorAssignmentRepository assignments = mock(MentorAssignmentRepository.class);
    private final MentoringMeetingRepository meetings = mock(MentoringMeetingRepository.class);
    private final MentoringActionItemRepository items = mock(MentoringActionItemRepository.class);
    private final MentoringService service = new MentoringService(assignments, meetings, items, mock(UserAccountRepository.class),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static UserAccount account(long id, String loginId) {
        UserAccount account = new UserAccount(loginId, "hash");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private MentorAssignment pair() {
        MentorAssignment pair = new MentorAssignment(account(1L, "faculty1"), account(2L, "student1"), NOW);
        ReflectionTestUtils.setField(pair, "id", 10L);
        when(assignments.findById(10L)).thenReturn(Optional.of(pair));
        MentoringMeeting meeting = new MentoringMeeting(pair, NOW, "First meeting", "Plan the semester.", "Seems anxious about exams.", NOW);
        ReflectionTestUtils.setField(meeting, "id", 20L);
        when(meetings.findForAssignment(10L)).thenReturn(List.of(meeting));
        when(items.findForMeetings(any())).thenReturn(List.of());
        return pair;
    }

    @Test
    void privateNotesReachTheMentorOnly() {
        pair();
        assertThat(service.pairing(1L, 10L).meetings().getFirst().privateNotes()).isEqualTo("Seems anxious about exams.");
        MentoringService.MeetingView forStudent = service.pairing(2L, 10L).meetings().getFirst();
        assertThat(forStudent.privateNotes()).isNull();
        assertThat(forStudent.sharedNotes()).isEqualTo("Plan the semester.");
    }

    @Test
    void othersGetTheSame404AndOnlyTheMentorRecordsMeetings() {
        pair();
        assertThatThrownBy(() -> service.pairing(3L, 10L)).isInstanceOf(ApiException.class).hasMessage(MentoringService.NOT_FOUND);
        assertThatThrownBy(() -> service.addMeeting(2L, 10L, NOW, "Mine", null, null)).isInstanceOf(ApiException.class)
                .hasMessage(MentoringService.MENTOR_ONLY);
    }

    @Test
    void anEndedPairTakesNoNewMeetings() {
        pair().end(NOW);
        assertThatThrownBy(() -> service.addMeeting(1L, 10L, NOW, "Late", null, null)).isInstanceOf(ApiException.class)
                .hasMessage(MentoringService.ENDED);
    }
}
