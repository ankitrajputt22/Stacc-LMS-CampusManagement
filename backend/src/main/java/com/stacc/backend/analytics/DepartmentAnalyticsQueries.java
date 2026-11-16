package com.stacc.backend.analytics;

import java.util.Collection;
import java.util.List;

import com.stacc.backend.academic.department.Department;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only counts for one department's view, bounded by the department and the current academic session. */
public interface DepartmentAnalyticsQueries extends Repository<Department, Long> {

    interface ProgramCount {
        String getCode();
        String getName();
        long getStudents();
    }

    /** The department's programs with their students enrolled in a semester of the current session. */
    @Query("""
            select program.code as code, program.name as name,
              (select count(enrollment) from SemesterEnrollment enrollment
               where enrollment.semester.program = program and enrollment.semester.academicSession.current = true
                 and enrollment.status = com.stacc.backend.academic.enrollment.SemesterEnrollmentStatus.ENROLLED) as students
            from Program program where program.department.id = :departmentId order by program.code
            """)
    List<ProgramCount> countProgramStudents(@Param("departmentId") Long departmentId);

    interface OfferingCount {
        Long getOfferingId();
        String getCourseCode();
        String getCourseName();
        String getProgramCode();
        int getSemesterNumber();
        long getEnrolled();
        long getPublishedResults();
    }

    /** Offerings of the department's courses in the current session, with enrolled students and published results. */
    @Query("""
            select offering.id as offeringId, course.code as courseCode, course.name as courseName, program.code as programCode,
              semester.semesterNumber as semesterNumber,
              (select count(enrollment) from CourseEnrollment enrollment where enrollment.courseOffering = offering
                 and enrollment.status = com.stacc.backend.academic.enrollment.CourseEnrollmentStatus.ENROLLED) as enrolled,
              (select count(result) from CourseResult result where result.courseEnrollment.courseOffering = offering
                 and result.status = com.stacc.backend.academic.result.CourseResultStatus.PUBLISHED) as publishedResults
            from CourseOffering offering join offering.course course join offering.semester semester join semester.program program
            where course.department.id = :departmentId and semester.academicSession.current = true
            order by course.code, program.code, semester.semesterNumber, offering.id
            """)
    List<OfferingCount> countOfferings(@Param("departmentId") Long departmentId);

    interface AttendanceCount {
        Long getOfferingId();
        long getPresent();
        long getMarked();
    }

    /** Present and marked records of finalized attendance sessions, per offering. */
    @Query("""
            select record.attendanceSession.courseOffering.id as offeringId,
              sum(case when record.status = com.stacc.backend.academic.attendance.AttendanceStatus.PRESENT then 1 else 0 end) as present,
              count(record) as marked
            from AttendanceRecord record
            where record.attendanceSession.courseOffering.id in :offeringIds
              and record.attendanceSession.status = com.stacc.backend.academic.attendance.AttendanceSessionStatus.FINALIZED
              and record.status in (com.stacc.backend.academic.attendance.AttendanceStatus.PRESENT,
                                    com.stacc.backend.academic.attendance.AttendanceStatus.ABSENT)
            group by record.attendanceSession.courseOffering.id
            """)
    List<AttendanceCount> countAttendance(@Param("offeringIds") Collection<Long> offeringIds);
}
