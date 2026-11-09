package com.stacc.backend.todo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceSessionRepository;
import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.approval.ApprovalQueryService;
import com.stacc.backend.approval.ApprovalResponses.ApprovalTaskSummary;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessPolicy;
import com.stacc.backend.lms.api.LmsCourseSummaryResponse;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseQueryService;
import com.stacc.backend.lms.api.LmsFacultyManagedCourseResponse;
import com.stacc.backend.lms.api.LmsStudentCourseQueryService;
import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.course.LmsCourseStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a person's To-Do list live on every request, one part per role and permission held: a student's unsubmitted
 * assignments (from the courses the student course list allows), a teacher's work waiting for grading and open
 * attendance (from the offerings taught), and anyone's pending approval tasks (from the approval engine's own rule).
 * Nothing is stored but dismissals. Keys name each item so a dismissal can hide it.
 */
@Service
public class TodoService {

    public static final int LIMIT = 20;
    private static final Duration PAST_DUE_WINDOW = Duration.ofDays(7);
    private static final Pattern KEY = Pattern.compile("^[A-Z_]{3,30}:[0-9:]{1,60}$");

    public record TodoItem(String key, String kind, String title, String context, Instant dueAt, String actionPath) {
    }

    private final LmsStudentCourseQueryService studentCourses;
    private final LmsFacultyManagedCourseQueryService facultyCourses;
    private final TodoAssignmentQueries assignments;
    private final AttendanceSessionRepository attendanceSessions;
    private final ApprovalQueryService approvals;
    private final TodoDismissalRepository dismissals;
    private final Clock clock;

    public TodoService(LmsStudentCourseQueryService studentCourses, LmsFacultyManagedCourseQueryService facultyCourses,
            TodoAssignmentQueries assignments, AttendanceSessionRepository attendanceSessions, ApprovalQueryService approvals,
            TodoDismissalRepository dismissals, Clock clock) {
        this.studentCourses = studentCourses;
        this.facultyCourses = facultyCourses;
        this.assignments = assignments;
        this.attendanceSessions = attendanceSessions;
        this.approvals = approvals;
        this.dismissals = dismissals;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TodoItem> list(Long accountId, Collection<? extends GrantedAuthority> authorities) {
        Set<String> held = AuthorityUtils.authorityListToSet(authorities);
        List<TodoItem> items = new ArrayList<>();
        if (held.contains("ROLE_STUDENT") && held.contains("LMS_COURSE_VIEW")) {
            items.addAll(studentItems(accountId));
        }
        if (held.contains("ROLE_FACULTY") && held.contains("FACULTY_COURSE_VIEW")) {
            items.addAll(facultyItems(accountId, held.contains("LMS_ASSIGNMENT_GRADE"), held.contains("ATTENDANCE_MANAGE")));
        }
        ApprovalActor actor = ApprovalActor.of(accountId, authorities);
        if (actor.taskView()) {
            for (ApprovalTaskSummary task : approvals.tasks(actor, 0, LIMIT).items()) {
                items.add(new TodoItem("APPROVAL:" + task.approvalRequestStepId(), "APPROVAL",
                        "Approve: " + task.subject(), task.workflowName() + " · from " + task.requesterLoginId(), null,
                        approvalPath(task)));
            }
        }
        Set<String> dismissed = dismissals.findKeysByAccountId(accountId);
        return items.stream()
                .filter(item -> !dismissed.contains(item.key()))
                .sorted(Comparator.comparing(TodoItem::dueAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Hides one item for this account. Dismissing again changes nothing. */
    @Transactional
    public void dismiss(Long accountId, String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This is not a To-Do item.");
        }
        if (!dismissals.existsByUserAccountIdAndItemKey(accountId, key)) {
            try {
                dismissals.saveAndFlush(new TodoDismissal(accountId, key, clock.instant().truncatedTo(ChronoUnit.MICROS)));
            } catch (DataIntegrityViolationException alreadyDismissed) {
                // Dismissed by another request at the same moment: the result is the same.
            }
        }
    }

    private List<TodoItem> studentItems(Long accountId) {
        Map<Long, LmsCourseSummaryResponse> courses = new LinkedHashMap<>();
        studentCourses.getAccessibleCourses(accountId).forEach(course -> courses.put(course.lmsCourseId(), course));
        if (courses.isEmpty()) {
            return List.of();
        }
        Instant from = clock.instant().minus(PAST_DUE_WINDOW);
        List<TodoItem> items = new ArrayList<>();
        for (LmsAssignment assignment : assignments.findUnsubmitted(courses.keySet(), accountId, from, PageRequest.of(0, LIMIT))) {
            Long courseId = assignment.getLmsCourse().getId();
            items.add(new TodoItem("ASSIGNMENT:" + assignment.getId(), "ASSIGNMENT", "Submit: " + assignment.getTitle(),
                    courses.get(courseId).courseCode(), assignment.getDueAt(),
                    "/courses/" + courseId + "/assignments/" + assignment.getId()));
        }
        return items;
    }

    private List<TodoItem> facultyItems(Long accountId, boolean grades, boolean attendance) {
        List<LmsFacultyManagedCourseResponse> taught = facultyCourses.getManagedCourses(accountId);
        Map<Long, LmsFacultyManagedCourseResponse> openCourses = new LinkedHashMap<>();
        Map<Long, LmsFacultyManagedCourseResponse> offerings = new LinkedHashMap<>();
        for (LmsFacultyManagedCourseResponse course : taught) {
            offerings.putIfAbsent(course.courseOfferingId(), course);
            if (course.lmsCourseId() != null
                    && LmsFacultyAccessPolicy.isOpenToFaculty(LmsCourseStatus.valueOf(course.lmsCourseStatus()))) {
                openCourses.putIfAbsent(course.lmsCourseId(), course);
            }
        }
        List<TodoItem> items = new ArrayList<>();
        if (grades && !openCourses.isEmpty()) {
            for (TodoAssignmentQueries.WaitingGrading waiting : assignments.findWaitingGrading(openCourses.keySet(), PageRequest.of(0, LIMIT))) {
                items.add(new TodoItem("GRADING:" + waiting.getAssignmentId() + ":" + waiting.getWaiting(), "GRADING",
                        "Grade: " + waiting.getTitle(),
                        openCourses.get(waiting.getLmsCourseId()).courseCode() + " · " + waiting.getWaiting() + " waiting", null,
                        "/teaching/" + waiting.getLmsCourseId() + "/assignments/" + waiting.getAssignmentId() + "/submissions"));
            }
        }
        if (attendance && !offerings.isEmpty()) {
            for (AttendanceSession session : attendanceSessions.findOpenByCourseOfferingIdIn(offerings.keySet(), PageRequest.of(0, LIMIT))) {
                Long offeringId = session.getCourseOffering().getId();
                items.add(new TodoItem("ATTENDANCE:" + session.getId(), "ATTENDANCE", "Finish attendance",
                        offerings.get(offeringId).courseCode(), session.getHeldAt(), "/attendance/" + offeringId));
            }
        }
        return items;
    }

    // Only links to pages that exist for the request's kind; the page checks access again.
    private static String approvalPath(ApprovalTaskSummary task) {
        return switch (task.sourceType()) {
            case "HOSTEL_APPLICATION" -> "/admin/hostel/applications/" + task.sourceId();
            case "CAMPUS_SERVICE_REQUEST" -> "/admin/campus/requests/" + task.sourceId();
            default -> null;
        };
    }
}
