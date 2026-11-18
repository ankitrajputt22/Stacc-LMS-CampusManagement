package com.stacc.backend.academic.exam;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.academic.enrollment.CourseEnrollment;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.academic.semester.SemesterStatus;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.person.PersonProfile;
import com.stacc.backend.identity.person.PersonProfileRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ExamService {

    static final String SESSION_NOT_FOUND = "Exam session not found.";
    static final String SLOT_NOT_FOUND = "Exam not found.";

    public record SlotView(Long slotId, Long courseOfferingId, String courseCode, String courseName, String sectionCode,
            LocalDate examDate, LocalTime startsAt, LocalTime endsAt, String hall, int hallSeats, long seatsGiven,
            long studentsEnrolled) {
    }

    public record SessionView(Long examSessionId, Long semesterId, String name, ExamSession.Status status, List<SlotView> exams) {
    }

    public record MyExam(Long slotId, String sessionName, String courseCode, String courseName, List<String> teachers,
            LocalDate examDate, LocalTime startsAt, LocalTime endsAt, String hall, Integer seatNumber) {
    }

    public record SeatPlan(Long slotId, String hall, int hallSeats, int seatsGiven, int added) {
    }

    private final ExamSessionRepository sessions;
    private final ExamSlotRepository slots;
    private final ExamSeatRepository seats;
    private final SemesterRepository semesters;
    private final CourseOfferingRepository offerings;
    private final CourseEnrollmentRepository enrollments;
    private final TeachingAssignmentRepository teaching;
    private final PersonProfileRepository people;
    private final Clock clock;

    public ExamService(ExamSessionRepository sessions, ExamSlotRepository slots, ExamSeatRepository seats, SemesterRepository semesters,
            CourseOfferingRepository offerings, CourseEnrollmentRepository enrollments, TeachingAssignmentRepository teaching,
            PersonProfileRepository people, Clock clock) {
        this.sessions = sessions;
        this.slots = slots;
        this.seats = seats;
        this.semesters = semesters;
        this.offerings = offerings;
        this.enrollments = enrollments;
        this.teaching = teaching;
        this.people = people;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SessionView> forSemester(Long semesterId) {
        List<ExamSession> found = sessions.findAllBySemester(semesterId);
        if (found.isEmpty()) {
            return List.of();
        }
        List<ExamSlot> live = slots.findLiveForSessions(found.stream().map(ExamSession::getId).toList());
        Map<Long, Long> given = live.isEmpty() ? Map.of()
                : seats.countForSlots(live.stream().map(ExamSlot::getId).toList()).stream()
                        .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return found.stream().map(session -> new SessionView(session.getId(), semesterId, session.getName(), session.getStatus(),
                live.stream().filter(slot -> slot.getExamSession().getId().equals(session.getId()))
                        .map(slot -> slotView(slot, given.getOrDefault(slot.getId(), 0L))).toList()))
                .toList();
    }

    public SessionView createSession(Long semesterId, String name) {
        Semester semester = (semesterId == null ? Optional.<Semester>empty() : semesters.findById(semesterId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Semester not found."));
        if (semester.getStatus() == SemesterStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "A closed semester takes no new exam session.");
        }
        String text = name == null ? "" : name.strip();
        if (text.isEmpty() || text.length() > 150) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Name the exam session in at most 150 characters.");
        }
        ExamSession saved = sessions.saveAndFlush(new ExamSession(semester, text, clock.instant()));
        return new SessionView(saved.getId(), semesterId, saved.getName(), saved.getStatus(), List.of());
    }

    public SlotView addExam(Long sessionId, Long courseOfferingId, LocalDate date, LocalTime startsAt, LocalTime endsAt, String hall,
            Integer hallSeats) {
        ExamSession session = sessions.findByIdForUpdate(sessionId == null ? -1 : sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SESSION_NOT_FOUND));
        CourseOffering offering = (courseOfferingId == null ? Optional.<CourseOffering>empty() : offerings.findById(courseOfferingId))
                .filter(found -> found.getSemester().getId().equals(session.getSemester().getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Course offering not found in this semester."));
        if (date == null || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose the day, and a start and an end time with the end after the start.");
        }
        String room = hall == null ? "" : hall.strip().toUpperCase(Locale.ROOT);
        if (room.isEmpty() || room.length() > 40) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a hall of at most 40 characters.");
        }
        if (hallSeats == null || hallSeats < 1 || hallSeats > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A hall has 1 to 1,000 seats.");
        }
        try {
            ExamSlot saved = slots.saveAndFlush(new ExamSlot(session, offering, date, startsAt, endsAt, room, hallSeats, clock.instant()));
            return slotView(saved, 0);
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, offering.getCourse().getCode() + " already has an exam in this session.");
        }
    }

    public void removeExam(Long slotId) {
        ExamSlot slot = slots.findByIdForUpdate(slotId == null ? -1 : slotId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SLOT_NOT_FOUND));
        slot.remove(clock.instant());
        slots.saveAndFlush(slot);
    }

    public SeatPlan seatPlan(Long slotId) {
        ExamSlot slot = slots.findByIdForUpdate(slotId == null ? -1 : slotId)
                .filter(found -> found.getRemovedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SLOT_NOT_FOUND));
        List<ExamSeat> existing = seats.findForSlotForUpdate(slot.getId());
        Set<Long> seated = existing.stream().map(seat -> seat.getCourseEnrollment().getId()).collect(Collectors.toSet());
        List<CourseEnrollment> waiting = enrollments.findAllCurrentWithAccountByCourseOfferingId(slot.getCourseOffering().getId())
                .stream().filter(enrollment -> !seated.contains(enrollment.getId())).toList();
        if (existing.size() + waiting.size() > slot.getHallSeats()) {
            throw new ApiException(HttpStatus.CONFLICT, "Hall " + slot.getHall() + " has " + slot.getHallSeats() + " seats, but "
                    + (existing.size() + waiting.size()) + " students take this exam.");
        }
        Set<Integer> taken = existing.stream().map(ExamSeat::getSeatNumber).collect(Collectors.toCollection(HashSet::new));
        int next = 1;
        for (CourseEnrollment enrollment : waiting) {
            while (taken.contains(next)) {
                next++;
            }
            seats.save(new ExamSeat(slot, enrollment, next, clock.instant()));
            taken.add(next);
        }
        seats.flush();
        return new SeatPlan(slot.getId(), slot.getHall(), slot.getHallSeats(), existing.size() + waiting.size(), waiting.size());
    }

    public SessionView publish(Long sessionId) {
        ExamSession session = sessions.findByIdForUpdate(sessionId == null ? -1 : sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SESSION_NOT_FOUND));
        session.publish(clock.instant());
        sessions.saveAndFlush(session);
        return forSemester(session.getSemester().getId()).stream()
                .filter(view -> view.examSessionId().equals(session.getId())).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<MyExam> mine(Long accountId) {
        List<CourseEnrollment> mine = enrollments.findAllWithStudentByUserAccountId(accountId).stream()
                .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED
                        && enrollment.getSemesterEnrollment().getStatus() == SemesterEnrollmentStatus.ENROLLED)
                .toList();
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseEnrollment> byOffering = mine.stream()
                .collect(Collectors.toMap(enrollment -> enrollment.getCourseOffering().getId(), Function.identity(), (a, b) -> a));
        List<ExamSession> published = sessions.findPublishedForSemesters(
                mine.stream().map(enrollment -> enrollment.getCourseOffering().getSemester().getId()).distinct().toList());
        if (published.isEmpty()) {
            return List.of();
        }
        List<ExamSlot> exams = slots.findLiveForSessions(published.stream().map(ExamSession::getId).toList()).stream()
                .filter(slot -> byOffering.containsKey(slot.getCourseOffering().getId())).toList();
        Map<Long, Integer> seatBySlot = new java.util.HashMap<>();
        seats.findForEnrollments(mine.stream().map(CourseEnrollment::getId).toList())
                .forEach(seat -> seatBySlot.put(seat.getExamSlot().getId(), seat.getSeatNumber()));
        Map<Long, List<String>> teachers = teachersOf(exams.stream().map(slot -> slot.getCourseOffering().getId()).distinct().toList());
        List<MyExam> result = new ArrayList<>();
        for (ExamSlot slot : exams) {
            CourseOffering offering = slot.getCourseOffering();
            result.add(new MyExam(slot.getId(), slot.getExamSession().getName(), offering.getCourse().getCode(), offering.getCourse().getName(),
                    teachers.getOrDefault(offering.getId(), List.of()), slot.getExamDate(), slot.getStartsAt(), slot.getEndsAt(),
                    slot.getHall(), seatBySlot.get(slot.getId())));
        }
        result.sort(Comparator.comparing(MyExam::examDate).thenComparing(MyExam::startsAt));
        return result;
    }

    private Map<Long, List<String>> teachersOf(List<Long> offeringIds) {
        if (offeringIds.isEmpty()) {
            return Map.of();
        }
        var active = teaching.findAllWithFacultyByCourseOfferingIdIn(offeringIds).stream()
                .filter(assignment -> assignment.getStatus() == TeachingAssignmentStatus.ACTIVE)
                .sorted(Comparator.comparing((com.stacc.backend.academic.teaching.TeachingAssignment assignment) -> assignment.getRole().name()).reversed())
                .toList();
        Map<Long, String> names = people.findAllByUserAccountIdIn(active.stream()
                        .map(assignment -> assignment.getFacultyProfile().getUserAccount().getId()).distinct().toList())
                .stream().collect(Collectors.toMap(person -> person.getUserAccount().getId(), PersonProfile::displayName));
        return active.stream().collect(Collectors.groupingBy(assignment -> assignment.getCourseOffering().getId(),
                Collectors.mapping(assignment -> names.getOrDefault(assignment.getFacultyProfile().getUserAccount().getId(),
                        assignment.getFacultyProfile().getUserAccount().getLoginId()), Collectors.toList())));
    }

    private SlotView slotView(ExamSlot slot, long given) {
        CourseOffering offering = slot.getCourseOffering();
        long enrolled = enrollments.findAllCurrentWithAccountByCourseOfferingId(offering.getId()).size();
        return new SlotView(slot.getId(), offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                offering.getSection() == null ? null : offering.getSection().getCode(), slot.getExamDate(), slot.getStartsAt(),
                slot.getEndsAt(), slot.getHall(), slot.getHallSeats(), given, enrolled);
    }
}
