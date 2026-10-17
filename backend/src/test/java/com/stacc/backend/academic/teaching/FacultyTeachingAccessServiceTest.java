package com.stacc.backend.academic.teaching;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.stacc.backend.academic.attendance.AttendanceFixtures;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.identity.faculty.FacultyProfile;
import org.junit.jupiter.api.Test;

/** Faculty A (account 21) teaches offering 17, which has no LMS course; nothing here looks for one. */
class FacultyTeachingAccessServiceTest {

    private static final Long FACULTY_A = 21L;
    private static final Long FACULTY_B = 22L;

    private final TeachingAssignmentRepository teachingAssignments = mock(TeachingAssignmentRepository.class);
    private final FacultyTeachingAccessService service = new FacultyTeachingAccessService(teachingAssignments);

    private final CourseOffering offering = AttendanceFixtures.offering(17, "BCS301", "Data Structures");
    private final FacultyProfile facultyA = AttendanceFixtures.faculty(FACULTY_A, 61);
    private final TeachingAssignment teachingA = AttendanceFixtures.teaching(facultyA, offering);

    @Test
    void anActiveProfileWithAnActiveAssignmentTeachesTheOfferingWithoutAnyLmsCourse() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, 17L)).thenReturn(List.of(teachingA));

        assertTrue(service.canTeachCourseOffering(FACULTY_A, 17L));
        assertSame(teachingA, service.findCurrentTeachingAssignment(FACULTY_A, 17L).orElseThrow());
    }

    @Test
    void facultyWithoutAnAssignmentDoNotTeachIt() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_B, 17L)).thenReturn(List.of());

        assertFalse(service.canTeachCourseOffering(FACULTY_B, 17L));
    }

    @Test
    void anInactiveProfileOrAssignmentGivesNoAccess() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_A, 17L)).thenReturn(List.of(teachingA));

        facultyA.deactivate();
        assertFalse(service.canTeachCourseOffering(FACULTY_A, 17L));

        facultyA.activate();
        teachingA.deactivate();
        assertFalse(service.canTeachCourseOffering(FACULTY_A, 17L));
    }

    @Test
    void anAssignmentOfAnotherAccountNeverCounts() {
        when(teachingAssignments.findAllByUserAccountIdAndCourseOfferingId(FACULTY_B, 17L)).thenReturn(List.of(teachingA));

        assertFalse(service.canTeachCourseOffering(FACULTY_B, 17L));
    }

    @Test
    void missingIdsAreRefusedWithoutAQuery() {
        assertFalse(service.canTeachCourseOffering(null, 17L));
        assertFalse(service.canTeachCourseOffering(FACULTY_A, null));
        verify(teachingAssignments, never()).findAllByUserAccountIdAndCourseOfferingId(any(), any());
    }
}
