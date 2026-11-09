package com.stacc.backend.search;

import static com.stacc.backend.search.SearchTestSupport.actor;
import static com.stacc.backend.search.SearchTestSupport.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import com.stacc.backend.academic.calendar.AcademicCalendarEvent;
import com.stacc.backend.academic.calendar.AcademicCalendarEventCategory;
import com.stacc.backend.academic.calendar.AcademicCalendarEventQueryService;
import com.stacc.backend.academic.calendar.AcademicCalendarEventRepository;
import com.stacc.backend.academic.calendar.AcademicCalendarEventStatus;
import com.stacc.backend.academic.calendar.AcademicCalendarScope;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.department.DepartmentRepository;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.program.ProgramRepository;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.campus.notices.CampusNoticeRepository;
import com.stacc.backend.campus.services.CampusServiceRequestRepository;
import com.stacc.backend.hostel.HostelApplicationRepository;
import com.stacc.backend.support.helpdesk.HelpdeskAccessService;
import com.stacc.backend.support.helpdesk.HelpdeskTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DomainSearchProvidersTest {

    private final PageRequest five = PageRequest.of(0, 5);

    // --- Calendar

    private final AcademicCalendarEventQueryService eventQueries = mock(AcademicCalendarEventQueryService.class);
    private final AcademicCalendarEventRepository events = mock(AcademicCalendarEventRepository.class);
    private final CalendarSearchProvider calendar = new CalendarSearchProvider(eventQueries, events);

    @Test
    void adminsSearchEveryVisibleEventAndOthersOnlyTheirScopeTogether() {
        when(events.searchVisible(anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(events.searchVisibleForScope(any(), any(), anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(eventQueries.studentScope(90L)).thenReturn(new AcademicCalendarScope(Set.of(1L), Set.of(10L)));
        when(eventQueries.facultyScope(90L)).thenReturn(new AcademicCalendarScope(Set.of(2L), Set.of(10L)));

        calendar.search(SearchResultType.ACADEMIC_CALENDAR_EVENT,
                context(actor(1, Set.of("ADMIN"), "ACADEMIC_CALENDAR_VIEW"), "exam"), five);
        verify(events).searchVisible("exam", "exam%", "%exam%", five);

        calendar.search(SearchResultType.ACADEMIC_CALENDAR_EVENT,
                context(actor(90, Set.of("STUDENT", "FACULTY"), "ACADEMIC_CALENDAR_VIEW"), "exam"), five);
        verify(events).searchVisibleForScope(eq(Set.of(1L, 2L)), eq(Set.of(10L)), any(), any(), any(), any());
    }

    @Test
    void anEmptyScopeOrNoCalendarPermissionSendsNoQuery() {
        when(eventQueries.studentScope(70L)).thenReturn(AcademicCalendarScope.NONE);

        assertThat(calendar.search(SearchResultType.ACADEMIC_CALENDAR_EVENT,
                context(actor(70, Set.of("STUDENT"), "ACADEMIC_CALENDAR_VIEW"), "exam"), five).orElseThrow()
                .getTotalElements()).isZero();
        assertThat(calendar.search(SearchResultType.ACADEMIC_CALENDAR_EVENT,
                context(actor(70, Set.of("STUDENT"), "GLOBAL_SEARCH_USE"), "exam"), five)).isEmpty();
        verifyNoInteractions(events);
    }

    @Test
    void anEventOpensTheCalendarOnItsMonthAndSaysCancelledInWords() {
        AcademicSession session = mock(AcademicSession.class);
        when(session.getCode()).thenReturn("2026-27");
        AcademicCalendarEvent event = mock(AcademicCalendarEvent.class);
        when(event.getTitle()).thenReturn("Mid-semester exams");
        when(event.getAcademicSession()).thenReturn(session);
        when(event.getStartDate()).thenReturn(LocalDate.of(2026, 11, 3));
        when(event.getEndDate()).thenReturn(LocalDate.of(2026, 11, 5));
        when(event.getCategory()).thenReturn(AcademicCalendarEventCategory.EXAM);
        when(event.getStatus()).thenReturn(AcademicCalendarEventStatus.CANCELLED);

        SearchResult result = CalendarSearchProvider.resultOf(event);

        assertThat(result.actionPath()).isEqualTo("/calendar?month=2026-11");
        assertThat(result.subtitle()).isEqualTo("3 Nov 2026 – 5 Nov 2026 · Exam · 2026-27 · Cancelled");
    }

    // --- Campus

    private final CampusNoticeRepository notices = mock(CampusNoticeRepository.class);
    private final CampusServiceRequestRepository requests = mock(CampusServiceRequestRepository.class);
    private final CampusSearchProvider campus = new CampusSearchProvider(notices, requests);

    @Test
    void noticesFollowEveryRoleTheAccountHoldsInOneQuery() {
        when(notices.searchFeed(anyBoolean(), anyBoolean(), anyBoolean(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Page.empty(five));

        campus.search(SearchResultType.CAMPUS_NOTICE,
                context(actor(90, Set.of("STUDENT", "FACULTY"), "CAMPUS_NOTICE_VIEW"), "library"), five);
        verify(notices).searchFeed(eq(true), eq(true), eq(false), any(), any(), any(), any());

        assertThat(campus.search(SearchResultType.CAMPUS_NOTICE,
                context(actor(70, Set.of("STUDENT"), "GLOBAL_SEARCH_USE"), "library"), five)).isEmpty();
    }

    @Test
    void requestersSearchTheirOwnRequestsAndOnlyTheCampusOfficeSearchesAll() {
        when(requests.searchOwn(anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(requests.searchAll(anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));

        campus.search(SearchResultType.CAMPUS_SERVICE_REQUEST,
                context(actor(80, Set.of("FACULTY"), "CAMPUS_SERVICE_REQUEST_VIEW_OWN"), "card"), five);
        verify(requests).searchOwn(eq(80L), any(), any(), any(), any());
        verify(requests, never()).searchAll(any(), any(), any(), any());

        campus.search(SearchResultType.CAMPUS_SERVICE_REQUEST,
                context(actor(1, Set.of("ADMIN"), "CAMPUS_SERVICE_MANAGE"), "card"), five);
        verify(requests).searchAll(any(), any(), any(), any());

        // ADMIN alone, or CAMPUS_SERVICE_MANAGE without ADMIN, is no pass to every request.
        assertThat(campus.search(SearchResultType.CAMPUS_SERVICE_REQUEST,
                context(actor(2, Set.of("ADMIN"), "CAMPUS_NOTICE_VIEW"), "card"), five)).isEmpty();
        campus.search(SearchResultType.CAMPUS_SERVICE_REQUEST,
                context(actor(70, Set.of("STUDENT"), "CAMPUS_SERVICE_MANAGE", "CAMPUS_SERVICE_REQUEST_VIEW_OWN"), "card"), five);
        verify(requests).searchOwn(eq(70L), any(), any(), any(), any());
    }

    // --- Helpdesk

    private final HelpdeskTicketRepository tickets = mock(HelpdeskTicketRepository.class);
    private final HelpdeskSearchProvider helpdesk = new HelpdeskSearchProvider(new HelpdeskAccessService(), tickets);

    @Test
    void ticketsAreOwnUnlessAnAdminMaySeeEveryTicket() {
        when(tickets.searchOwn(anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(tickets.searchAll(anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));

        helpdesk.search(SearchResultType.HELPDESK_TICKET,
                context(actor(70, Set.of("STUDENT"), "HELPDESK_TICKET_VIEW_OWN"), "projector"), five);
        verify(tickets).searchOwn(eq(70L), any(), any(), any(), any());

        helpdesk.search(SearchResultType.HELPDESK_TICKET,
                context(actor(2, Set.of("ADMIN"), "HELPDESK_TICKET_VIEW_OWN"), "projector"), five);
        verify(tickets).searchOwn(eq(2L), any(), any(), any(), any());
        verify(tickets, never()).searchAll(any(), any(), any(), any());

        helpdesk.search(SearchResultType.HELPDESK_TICKET,
                context(actor(1, Set.of("ADMIN"), "HELPDESK_TICKET_VIEW_OWN", "HELPDESK_TICKET_VIEW_ALL"), "projector"), five);
        verify(tickets).searchAll(any(), any(), any(), any());

        // VIEW_ALL without ADMIN is own-only, and without any ticket permission there is no section.
        helpdesk.search(SearchResultType.HELPDESK_TICKET,
                context(actor(71, Set.of("STUDENT"), "HELPDESK_TICKET_VIEW_ALL", "HELPDESK_TICKET_VIEW_OWN"), "projector"), five);
        verify(tickets).searchOwn(eq(71L), any(), any(), any(), any());
        assertThat(helpdesk.search(SearchResultType.HELPDESK_TICKET,
                context(actor(72, Set.of("STUDENT"), "GLOBAL_SEARCH_USE"), "projector"), five)).isEmpty();
    }

    // --- Hostel

    private final HostelApplicationRepository applications = mock(HostelApplicationRepository.class);
    private final HostelSearchProvider hostel = new HostelSearchProvider(applications);

    @Test
    void studentsSearchTheirOwnApplicationsTheOfficeAllAndFacultyNone() {
        when(applications.searchOwn(anyLong(), anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(applications.searchAll(anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));

        hostel.search(SearchResultType.HOSTEL_APPLICATION,
                context(actor(70, Set.of("STUDENT"), "HOSTEL_APPLICATION_VIEW_OWN"), "HST-0"), five);
        verify(applications).searchOwn(eq(70L), any(), any(), any(), any());

        hostel.search(SearchResultType.HOSTEL_APPLICATION, context(actor(1, Set.of("ADMIN"), "HOSTEL_MANAGE"), "HST-0"), five);
        verify(applications).searchAll(any(), any(), any(), any());

        assertThat(hostel.search(SearchResultType.HOSTEL_APPLICATION,
                context(actor(80, Set.of("FACULTY"), "HOSTEL_APPLICATION_VIEW_OWN", "HOSTEL_MANAGE"), "HST-0"), five)).isEmpty();
    }

    // --- Academic

    private final DepartmentRepository departments = mock(DepartmentRepository.class);
    private final ProgramRepository programs = mock(ProgramRepository.class);
    private final AcademicSessionRepository sessions = mock(AcademicSessionRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final AcademicSearchProvider academic = new AcademicSearchProvider(departments, programs, sessions,
            semesters, courses, offerings);

    @Test
    void academicDataNeedsTheAdminRoleWithItsOwnViewPermission() {
        when(departments.search(anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(offerings.search(anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));
        when(semesters.search(any(), anyString(), anyString(), anyString(), any())).thenReturn(Page.empty(five));

        SearchActor masters = actor(1, Set.of("ADMIN"), "ACADEMIC_MASTER_VIEW");
        assertThat(academic.search(SearchResultType.DEPARTMENT, context(masters, "CSE"), five)).isPresent();
        assertThat(academic.search(SearchResultType.COURSE_OFFERING, context(masters, "CSE"), five)).isEmpty();
        assertThat(academic.search(SearchResultType.COURSE_OFFERING,
                context(actor(1, Set.of("ADMIN"), "ACADEMIC_OFFERING_VIEW"), "CSE"), five)).isPresent();
        academic.search(SearchResultType.SEMESTER, context(masters, "semester 3"), five);
        verify(semesters).search(eq(3), eq("semester 3"), any(), any(), any());

        for (SearchActor other : List.of(actor(70, Set.of("STUDENT"), "ACADEMIC_MASTER_VIEW", "ACADEMIC_OFFERING_VIEW"),
                actor(80, Set.of("FACULTY"), "ACADEMIC_MASTER_VIEW"), actor(2, Set.of("ADMIN"), "GLOBAL_SEARCH_USE"))) {
            for (SearchResultType type : academic.types()) {
                assertThat(academic.search(type, context(other, "CSE"), five)).isEmpty();
            }
        }
        verifyNoInteractions(programs, sessions, courses);
    }

    @Test
    void countsComeFromTheSameScopedQueryAsTheItems() {
        // Two of the requester's own tickets match; the total is what the own-only query counted, never every ticket.
        when(tickets.searchOwn(eq(70L), anyString(), anyString(), anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(), five, 2));
        var page = helpdesk.search(SearchResultType.HELPDESK_TICKET,
                context(actor(70, Set.of("STUDENT"), "HELPDESK_TICKET_VIEW_OWN"), "projector"), five).orElseThrow();
        assertThat(page.getTotalElements()).isEqualTo(2);
        verify(tickets, never()).searchAll(any(), any(), any(), any());
    }
}
