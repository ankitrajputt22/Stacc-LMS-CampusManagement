package com.stacc.backend.lms.ai.planner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.calendar.personal.PersonalCalendarEvent;
import com.stacc.backend.calendar.personal.PersonalCalendarService;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardStore;
import com.stacc.backend.lms.ai.mastery.AiMasteryService;
import com.stacc.backend.lms.ai.mastery.AiOpenAssignments;
import com.stacc.backend.lms.ai.mastery.Mastery;
import com.stacc.backend.lms.course.LmsCourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiStudyPlanService {

    static final Duration REFRESH_AFTER = Duration.ofDays(21);
    static final String WRONG_LENGTH = "Plan 7 or 14 days.";
    static final String WRONG_MINUTES = "Plan from 30 to 240 minutes a day.";
    static final String CALENDAR_PREFIX = "Study plan · ";

    private final AiMasteryService mastery;
    private final AiFlashcardStore flashcards;
    private final AiOpenAssignments openAssignments;
    private final PersonalCalendarService calendar;
    private final AiStudySessionRepository sessions;
    private final LmsCourseRepository lmsCourses;
    private final EngagementProperties college;
    private final Clock clock;

    public AiStudyPlanService(AiMasteryService mastery, AiFlashcardStore flashcards, AiOpenAssignments openAssignments,
            PersonalCalendarService calendar, AiStudySessionRepository sessions, LmsCourseRepository lmsCourses,
            EngagementProperties college, Clock clock) {
        this.lmsCourses = lmsCourses;
        this.mastery = mastery;
        this.flashcards = flashcards;
        this.openAssignments = openAssignments;
        this.calendar = calendar;
        this.sessions = sessions;
        this.college = college;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StudyPlanner.Day> plan(Long accountId, Long lmsCourseId, int days, int minutesPerDay) {
        if (days != 7 && days != 14) {
            throw new ApiException(HttpStatus.BAD_REQUEST, WRONG_LENGTH);
        }
        if (minutesPerDay < 30 || minutesPerDay > 240) {
            throw new ApiException(HttpStatus.BAD_REQUEST, WRONG_MINUTES);
        }
        List<AiMasteryService.TopicMastery> topics = mastery.view(accountId, lmsCourseId);
        ZoneId zone = college.zone();
        Instant now = clock.instant();
        LocalDate start = now.atZone(zone).toLocalDate();
        LocalDate end = start.plusDays(days - 1L);
        List<StudyPlanner.Need> needs = new ArrayList<>();
        openAssignments.stillToHandIn(accountId, lmsCourseId, now).stream()
                .filter(assignment -> !assignment.getDueAt().atZone(zone).toLocalDate().isAfter(end.plusDays(1)))
                .forEach(assignment -> needs.add(new StudyPlanner.Need(StudyPlanner.Kind.ASSIGNMENT, assignment.getId(), assignment.getTitle(),
                        assignment.getDueAt().atZone(zone).toLocalDate())));
        topics.stream().filter(topic -> topic.result().level() == Mastery.Level.LEARNING)
                .sorted((first, second) -> Double.compare(first.result().mastery(), second.result().mastery()))
                .forEach(topic -> needs.add(new StudyPlanner.Need(StudyPlanner.Kind.PRACTISE_WEAK, topic.topicId(), topic.title(), null)));
        topics.stream().filter(topic -> topic.result().level() == Mastery.Level.NOT_STARTED)
                .forEach(topic -> needs.add(new StudyPlanner.Need(StudyPlanner.Kind.START_PAGE, topic.topicId(), topic.title(), null)));
        topics.stream().filter(topic -> topic.result().level() == Mastery.Level.PRACTISING
                        || topic.result().level() == Mastery.Level.MASTERED && topic.result().lastPractisedAt() != null
                        && topic.result().lastPractisedAt().isBefore(now.minus(REFRESH_AFTER)))
                .forEach(topic -> needs.add(new StudyPlanner.Need(StudyPlanner.Kind.REFRESH_PAGE, topic.topicId(), topic.title(), null)));
        Set<LocalDate> busy = new HashSet<>();
        for (PersonalCalendarEvent event : calendar.overlapping(accountId, start, end)) {
            if (event.getTitle().startsWith(CALENDAR_PREFIX)) {
                continue;
            }
            for (LocalDate day = event.getStartDate().isBefore(start) ? start : event.getStartDate();
                    !day.isAfter(event.getEndDate()) && !day.isAfter(end); day = day.plusDays(1)) {
                busy.add(day);
            }
        }
        boolean hasFlashcards = !flashcards.list(accountId, lmsCourseId, start).isEmpty();
        return StudyPlanner.plan(start, days, minutesPerDay, needs, busy, hasFlashcards);
    }

    @Transactional
    public int addToCalendar(Long accountId, Long lmsCourseId, int days, int minutesPerDay) {
        List<StudyPlanner.Day> planned = plan(accountId, lmsCourseId, days, minutesPerDay);
        if (planned.isEmpty()) {
            return 0;
        }
        String course = lmsCourses.findById(lmsCourseId).map(found -> found.getCourseOffering().getCourse().getCode()).orElse("course");
        String prefix = CALENDAR_PREFIX + course + ":";
        Set<LocalDate> alreadyPlanned = new HashSet<>();
        calendar.overlapping(accountId, planned.getFirst().date(), planned.getLast().date()).stream()
                .filter(event -> event.getTitle().startsWith(prefix))
                .forEach(event -> alreadyPlanned.add(event.getStartDate()));
        int added = 0;
        for (StudyPlanner.Day day : planned) {
            if (day.sessions().isEmpty() || alreadyPlanned.contains(day.date())) {
                continue;
            }
            String notes = day.sessions().stream().map(session -> session.title() + " (" + session.minutes() + " min)")
                    .collect(Collectors.joining("\n"));
            calendar.create(accountId, prefix + " " + day.planned() + " min", notes.length() > PersonalCalendarEvent.MAX_NOTES_LENGTH
                    ? notes.substring(0, PersonalCalendarEvent.MAX_NOTES_LENGTH) : notes, day.date(), day.date());
            added++;
        }
        return added;
    }

    @Transactional
    public int addToTodo(Long accountId, Long lmsCourseId, int days, int minutesPerDay) {
        int added = 0;
        for (StudyPlanner.Day day : plan(accountId, lmsCourseId, days, minutesPerDay)) {
            for (StudyPlanner.Session session : day.sessions()) {
                if (!sessions.existsByUserAccountIdAndLmsCourseIdAndPlannedOnAndTitle(accountId, lmsCourseId, day.date(), session.title())) {
                    sessions.save(new AiStudySession(lmsCourseId, accountId, day.date(), session.title(), session.minutes()));
                    added++;
                }
            }
        }
        return added;
    }

    @Transactional(readOnly = true)
    public List<AiStudySession> dueForTodo(Long accountId) {
        return sessions.findTop50ByUserAccountIdAndPlannedOnLessThanEqualOrderByPlannedOnAscIdAsc(accountId,
                clock.instant().atZone(college.zone()).toLocalDate());
    }

    public ZoneId zone() {
        return college.zone();
    }
}
