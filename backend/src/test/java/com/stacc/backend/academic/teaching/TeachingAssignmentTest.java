package com.stacc.backend.academic.teaching;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.stacc.backend.academic.course.Course;
import com.stacc.backend.academic.department.Department;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.program.Program;
import com.stacc.backend.academic.semester.Semester;
import com.stacc.backend.academic.session.AcademicSession;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

class TeachingAssignmentTest {

    private final Department computing = new Department("CSE", "Computer Science and Engineering");
    private final Department mathematics = new Department("MATH", "Mathematics");
    private final Program program = new Program(computing, "BTECH", "Bachelor of Technology", 8);
    private final AcademicSession session =
            new AcademicSession("2026-27", LocalDate.of(2026, 7, 1), LocalDate.of(2027, 6, 30));
    private final Semester semester = new Semester(program, session, 3);
    private final CourseOffering structures =
            new CourseOffering(new Course(computing, "BCS301", "Data Structures", new BigDecimal("4.00")), semester);
    private final CourseOffering discreteMaths =
            new CourseOffering(new Course(mathematics, "MAT301", "Discrete Mathematics", new BigDecimal("3.00")), semester);
    private final FacultyProfile facultyA =
            new FacultyProfile(new UserAccount("EMP-TEST-0001", "example-hash-value"), computing);
    private final FacultyProfile facultyB =
            new FacultyProfile(new UserAccount("EMP-TEST-0002", "example-hash-value"), computing);

    @Test
    void newAssignmentIsActiveAndLinksTheFacultyProfileAndTheOffering() {
        TeachingAssignment assignment = new TeachingAssignment(facultyA, structures);

        assertSame(facultyA, assignment.getFacultyProfile());
        assertSame(structures, assignment.getCourseOffering());
        assertEquals(TeachingAssignmentStatus.ACTIVE, assignment.getStatus());
    }

    @Test
    void facultyProfileAndOfferingAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new TeachingAssignment(null, structures));
        assertThrows(IllegalArgumentException.class, () -> new TeachingAssignment(facultyA, null));
    }

    @Test
    void assignmentCanBeMadeInactiveAndActiveAgain() {
        TeachingAssignment assignment = new TeachingAssignment(facultyA, structures);

        assignment.deactivate();
        assertEquals(TeachingAssignmentStatus.INACTIVE, assignment.getStatus());

        assignment.activate();
        assertEquals(TeachingAssignmentStatus.ACTIVE, assignment.getStatus());
    }

    @Test
    void courseAndSemesterAreReachedThroughTheOffering() {
        TeachingAssignment assignment = new TeachingAssignment(facultyA, structures);

        assertEquals("BCS301", assignment.getCourseOffering().getCourse().getCode());
        assertSame(semester, assignment.getCourseOffering().getSemester());
        assertEquals("EMP-TEST-0001", assignment.getFacultyProfile().getUserAccount().getLoginId());
    }

    @Test
    void oneFacultyMemberCanTeachSeveralOfferingsAndOneOfferingCanHaveSeveralFacultyMembers() {
        TeachingAssignment aTeachesStructures = new TeachingAssignment(facultyA, structures);
        TeachingAssignment aTeachesMaths = new TeachingAssignment(facultyA, discreteMaths);
        TeachingAssignment bTeachesStructures = new TeachingAssignment(facultyB, structures);

        assertSame(aTeachesStructures.getFacultyProfile(), aTeachesMaths.getFacultyProfile());
        assertNotSame(aTeachesStructures.getCourseOffering(), aTeachesMaths.getCourseOffering());
        assertSame(aTeachesStructures.getCourseOffering(), bTeachesStructures.getCourseOffering());
        assertNotSame(aTeachesStructures.getFacultyProfile(), bTeachesStructures.getFacultyProfile());
    }

    @Test
    void primaryDepartmentDoesNotLimitWhatAFacultyMemberTeaches() {
        // A computing faculty member may teach a course owned by the mathematics department.
        TeachingAssignment assignment = new TeachingAssignment(facultyA, discreteMaths);

        assertSame(computing, assignment.getFacultyProfile().getDepartment());
        assertSame(mathematics, assignment.getCourseOffering().getCourse().getDepartment());
        assertEquals(TeachingAssignmentStatus.ACTIVE, assignment.getStatus());
    }
}
