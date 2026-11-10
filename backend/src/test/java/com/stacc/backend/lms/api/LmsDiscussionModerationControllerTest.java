package com.stacc.backend.lms.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.DatabaseFreeApiTest;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.token.TestTokens;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import com.stacc.backend.lms.discussion.LmsDiscussionReply;
import com.stacc.backend.lms.discussion.LmsDiscussionService;
import com.stacc.backend.lms.discussion.LmsDiscussionThread;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Only faculty with the moderation permission hide, lock, and resolve; only students report; hidden replies reach students empty. */
class LmsDiscussionModerationControllerTest extends DatabaseFreeApiTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    @MockitoBean
    private LmsDiscussionService service;

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String token(String... authorities) {
        return TestTokens.valid("TEST-USER-1", 21L, authorities);
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void moderationNeedsTheFacultyRoleCourseViewAndTheModerationPermission() throws Exception {
        List<MockHttpServletRequestBuilder> routes = List.of(
                json(post("/api/v1/lms/faculty/courses/7/discussions/3/hide"), "{\"hidden\":true}"),
                json(post("/api/v1/lms/faculty/courses/7/discussions/3/lock"), "{\"locked\":true}"),
                json(post("/api/v1/lms/faculty/courses/7/discussions/3/replies/5/hide"), "{\"hidden\":true}"),
                get("/api/v1/lms/faculty/courses/7/discussion-reports"),
                json(post("/api/v1/lms/faculty/courses/7/discussion-reports/9/resolve"), "{\"hide\":false}"));
        for (String wrong : List.of(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_DISCUSSION_USE"),
                token("ROLE_FACULTY", "LMS_DISCUSSION_MODERATE"),
                token("ROLE_STUDENT", "LMS_COURSE_VIEW", "FACULTY_COURSE_VIEW", "LMS_DISCUSSION_USE", "LMS_DISCUSSION_MODERATE"),
                token("ROLE_ADMIN", "FACULTY_COURSE_VIEW", "LMS_DISCUSSION_MODERATE"))) {
            for (MockHttpServletRequestBuilder route : routes) {
                mockMvc.perform(as(wrong, route)).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void onlyStudentsReportAndAReasonIsRequired() throws Exception {
        MockHttpServletRequestBuilder report = json(post("/api/v1/lms/courses/7/discussions/3/reports"), "{\"reason\":\"Off topic\"}");
        mockMvc.perform(as(token("ROLE_FACULTY", "FACULTY_COURSE_VIEW", "LMS_DISCUSSION_USE", "LMS_COURSE_VIEW"), report))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(token("ROLE_STUDENT", "LMS_COURSE_VIEW", "LMS_DISCUSSION_USE"),
                json(post("/api/v1/lms/courses/7/discussions/3/reports"), "{\"reason\":\" \"}"))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void aHiddenReplyReachesStudentsWithoutItsAuthorOrTextButFacultySeeIt() {
        UserAccount author = new UserAccount("student2", "hash");
        ReflectionTestUtils.setField(author, "id", 31L);
        LmsDiscussionThread thread = new LmsDiscussionThread(ContentFixtures.lmsCourse(7), author, AuthorKind.STUDENT, "Help", "Question", NOW);
        LmsDiscussionReply reply = new LmsDiscussionReply(thread, author, AuthorKind.STUDENT, "Rude words", NOW);
        ReflectionTestUtils.setField(reply, "id", 5L);
        reply.hide(21L, NOW);
        LmsDiscussionService.ThreadDetail detail = new LmsDiscussionService.ThreadDetail(thread, List.of(reply));

        LmsDiscussionResponses.Reply seenByStudent =
                LmsDiscussionResponses.ThreadDetail.of(detail, new Viewer(40L, AuthorKind.STUDENT)).replies().get(0);
        assertThat(seenByStudent.hidden()).isTrue();
        assertThat(seenByStudent.body()).isNull();
        assertThat(seenByStudent.authorLoginId()).isNull();

        LmsDiscussionResponses.ThreadDetail seenByFaculty = LmsDiscussionResponses.ThreadDetail.of(detail, new Viewer(21L, AuthorKind.FACULTY));
        assertThat(seenByFaculty.replies().get(0).body()).isEqualTo("Rude words");
        assertThat(seenByFaculty.canModerate()).isTrue();
        assertThat(seenByFaculty.canReport()).isFalse();
    }
}
