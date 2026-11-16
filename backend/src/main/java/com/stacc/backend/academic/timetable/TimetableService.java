package com.stacc.backend.academic.timetable;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.enrollment.CourseEnrollmentStatus;
import com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.semester.SemesterStatus;
import com.stacc.backend.academic.session.AcademicSessionRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The class timetable (v0.16.2). Admins add and remove weekly slots of course offerings; adding one locks the academic
 * session's row and then reads that day's slots, so two slots of the same room, or two classes of the same students,
 * can never overlap. Students read the slots of the offerings they are enrolled in now, faculty those they teach now.
 */
@Service
@Transactional
public class TimetableService {

    static final String OFFERING_NOT_FOUND = "Course offering not found.";
    static final String SLOT_NOT_FOUND = "Timetable slot not found.";
    static final LocalTime EARLIEST = LocalTime.of(6, 0);
    static final LocalTime LATEST = LocalTime.of(22, 0);

    public record SlotView(Long slotId, Long courseOfferingId, String courseCode, String courseName, String sectionCode,
            DayOfWeek day, LocalTime startsAt, LocalTime endsAt, String room, ClassTimetableSlot.Kind kind) {

        static SlotView of(ClassTimetableSlot slot) {
            CourseOffering offering = slot.getCourseOffering();
            return new SlotView(slot.getId(), offering.getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                    offering.getSection() == null ? null : offering.getSection().getCode(), slot.getDay(), slot.getStartsAt(),
                    slot.getEndsAt(), slot.getRoom(), slot.getKind());
        }
    }

    /** The signed-in account's week: a part for studying and a part for teaching, each null without that role. */
    public record MyTimetable(List<SlotView> studying, List<SlotView> teaching) {
    }

    private final ClassTimetableSlotRepository slots;
    private final CourseOfferingRepository offerings;
    private final AcademicSessionRepository sessions;
    private final CourseEnrollmentRepository courseEnrollments;
    private final TeachingAssignmentRepository teaching;
    private final Clock clock;

    public TimetableService(ClassTimetableSlotRepository slots, CourseOfferingRepository offerings, AcademicSessionRepository sessions,
            CourseEnrollmentRepository courseEnrollments, TeachingAssignmentRepository teaching, Clock clock) {
        this.slots = slots;
        this.offerings = offerings;
        this.sessions = sessions;
        this.courseEnrollments = courseEnrollments;
        this.teaching = teaching;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SlotView> forSemester(Long semesterId) {
        return slots.findActiveForSemester(semesterId).stream().map(SlotView::of).toList();
    }

    /** Adds a weekly slot, refusing a clash of room or of students with any slot of the same session. */
    public SlotView add(Long courseOfferingId, DayOfWeek day, LocalTime startsAt, LocalTime endsAt, String room,
            ClassTimetableSlot.Kind kind) {
        CourseOffering offering = (courseOfferingId == null ? Optional.<CourseOffering>empty() : offerings.findById(courseOfferingId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, OFFERING_NOT_FOUND));
        if (day == null || startsAt == null || endsAt == null || kind == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a day, a start and an end time, and the kind of class.");
        }
        if (!endsAt.isAfter(startsAt) || startsAt.isBefore(EARLIEST) || endsAt.isAfter(LATEST)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A class runs between 06:00 and 22:00 and ends after it starts.");
        }
        String validRoom = room == null ? "" : room.strip().toUpperCase(Locale.ROOT);
        if (validRoom.isEmpty() || validRoom.length() > ClassTimetableSlot.MAX_ROOM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Enter a room of at most 40 characters.");
        }
        if (offering.getStatus() == CourseOfferingStatus.CLOSED || offering.getSemester().getStatus() == SemesterStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "A closed offering or semester takes no new classes.");
        }
        sessions.findByIdForUpdate(offering.getSemester().getAcademicSession().getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, OFFERING_NOT_FOUND));
        for (ClassTimetableSlot other : slots.findActiveForSessionOnDay(offering.getSemester().getAcademicSession().getId(), day.getValue())) {
            if (!other.overlaps(day, startsAt, endsAt)) {
                continue;
            }
            String code = other.getCourseOffering().getCourse().getCode();
            if (other.getRoom().equalsIgnoreCase(validRoom)) {
                throw new ApiException(HttpStatus.CONFLICT, "Room " + validRoom + " is already used by " + code + " at that time.");
            }
            if (sameStudents(offering, other.getCourseOffering())) {
                throw new ApiException(HttpStatus.CONFLICT, "These students already have " + code + " at that time.");
            }
        }
        return SlotView.of(slots.saveAndFlush(new ClassTimetableSlot(offering, day, startsAt, endsAt, validRoom, kind, clock.instant())));
    }

    /** Removes a slot from the week; it is kept as history. Removing it again changes nothing. */
    public void remove(Long slotId) {
        ClassTimetableSlot slot = (slotId == null ? Optional.<ClassTimetableSlot>empty() : slots.findById(slotId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SLOT_NOT_FOUND));
        slot.remove(clock.instant());
        slots.saveAndFlush(slot);
    }

    /** The week of the signed-in account, from its current enrollments and teaching assignments. */
    @Transactional(readOnly = true)
    public MyTimetable mine(Long accountId, boolean asStudent, boolean asFaculty) {
        List<SlotView> studying = null;
        if (asStudent) {
            List<Long> ids = courseEnrollments.findAllWithStudentByUserAccountId(accountId).stream()
                    .filter(enrollment -> enrollment.getStatus() == CourseEnrollmentStatus.ENROLLED
                            && enrollment.getSemesterEnrollment().getStatus() == SemesterEnrollmentStatus.ENROLLED)
                    .map(enrollment -> enrollment.getCourseOffering().getId()).distinct().toList();
            studying = ids.isEmpty() ? List.of() : slots.findActiveForOfferings(ids).stream().map(SlotView::of).toList();
        }
        List<SlotView> taught = null;
        if (asFaculty) {
            List<Long> ids = teaching.findAllWithCourseDetailsByUserAccountId(accountId).stream()
                    .filter(assignment -> assignment.getStatus() == TeachingAssignmentStatus.ACTIVE
                            && assignment.getCourseOffering().getStatus() != CourseOfferingStatus.CLOSED)
                    .map(assignment -> assignment.getCourseOffering().getId()).distinct().toList();
            taught = ids.isEmpty() ? List.of() : slots.findActiveForOfferings(ids).stream().map(SlotView::of).toList();
        }
        return new MyTimetable(studying, taught);
    }

    // Two offerings share students when they are of the same semester and either is for the whole semester or both are
    // for the same section. The same offering always shares its students.
    private static boolean sameStudents(CourseOffering one, CourseOffering other) {
        if (one.getId().equals(other.getId())) {
            return true;
        }
        if (!one.getSemester().getId().equals(other.getSemester().getId())) {
            return false;
        }
        return one.getSection() == null || other.getSection() == null || one.getSection().getId().equals(other.getSection().getId());
    }
}
