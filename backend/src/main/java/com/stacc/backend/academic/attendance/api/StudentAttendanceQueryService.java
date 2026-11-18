package com.stacc.backend.academic.attendance.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.stacc.backend.academic.attendance.AttendanceRecord;
import com.stacc.backend.academic.attendance.AttendanceRecordRepository;
import com.stacc.backend.academic.attendance.AttendanceSession;
import com.stacc.backend.academic.attendance.AttendanceStatus;
import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.semester.Semester;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentAttendanceQueryService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final Comparator<StudentAttendanceResponse.Course> BY_COURSE_CODE = Comparator
            .comparing(StudentAttendanceResponse.Course::courseCode)
            .thenComparing(StudentAttendanceResponse.Course::courseOfferingId);

    private final AttendanceRecordRepository records;

    public StudentAttendanceQueryService(AttendanceRecordRepository records) {
        this.records = records;
    }

    public StudentAttendanceResponse getMyAttendance(Long userAccountId) {
        if (userAccountId == null) {
            return new StudentAttendanceResponse(List.of());
        }
        Map<Long, List<AttendanceRecord>> byOffering = new LinkedHashMap<>();
        for (AttendanceRecord record : records.findAllFinalizedByUserAccountId(userAccountId)) {
            AttendanceSession session = record.getAttendanceSession();
            if (session.isFinalized() && record.isMarked()) {
                byOffering.computeIfAbsent(session.getCourseOffering().getId(), id -> new ArrayList<>()).add(record);
            }
        }
        List<StudentAttendanceResponse.Course> courses = byOffering.values().stream()
                .map(StudentAttendanceQueryService::courseOf)
                .sorted(BY_COURSE_CODE)
                .toList();
        return new StudentAttendanceResponse(courses);
    }

    static BigDecimal percentage(int presentCount, int totalSessions) {
        if (totalSessions == 0) {
            return null;
        }
        return BigDecimal.valueOf(presentCount).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalSessions), 2, RoundingMode.HALF_UP);
    }

    private static StudentAttendanceResponse.Course courseOf(List<AttendanceRecord> sessionRecords) {
        CourseOffering offering = sessionRecords.getFirst().getAttendanceSession().getCourseOffering();
        Course course = offering.getCourse();
        Semester semester = offering.getSemester();
        int present = (int) sessionRecords.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT).count();
        int total = sessionRecords.size();
        List<StudentAttendanceResponse.Session> sessions = sessionRecords.stream()
                .map(record -> new StudentAttendanceResponse.Session(
                        record.getAttendanceSession().getId(),
                        record.getAttendanceSession().getHeldAt(),
                        record.getAttendanceSession().getTopic(),
                        record.getStatus().name(),
                        record.getId()))
                .toList();
        return new StudentAttendanceResponse.Course(
                offering.getId(),
                course.getCode(),
                course.getName(),
                semester.getSemesterNumber(),
                semester.getProgram().getCode(),
                semester.getAcademicSession().getCode(),
                total,
                present,
                total - present,
                percentage(present, total),
                sessions,
                isLow(percentage(present, total)));
    }

    public static final BigDecimal MINIMUM_PERCENT = new BigDecimal("75");

    public static boolean isLow(BigDecimal percentage) {
        return percentage != null && percentage.compareTo(MINIMUM_PERCENT) < 0;
    }
}
