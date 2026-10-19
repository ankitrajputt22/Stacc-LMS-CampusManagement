package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingCreate;
import com.stacc.backend.academic.admin.OfferingEnrollmentRequests.CourseOfferingUpdate;
import com.stacc.backend.academic.admin.OfferingEnrollmentResponses.CourseOfferingResponse;
import com.stacc.backend.academic.course.CourseRepository;
import com.stacc.backend.academic.offering.CourseOffering;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.offering.CourseOfferingStatus;
import com.stacc.backend.academic.semester.SemesterRepository;
import com.stacc.backend.common.error.ApiException;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

class CourseOfferingManagementServiceTest {

    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final CourseRepository courses = mock(CourseRepository.class);
    private final SemesterRepository semesters = mock(SemesterRepository.class);
    private final CourseOfferingManagementService service = new CourseOfferingManagementService(offerings, courses, semesters);
    private final EnrollmentFixtures college = new EnrollmentFixtures();

    @BeforeEach
    void setUp() {
        when(courses.findById(13L)).thenReturn(Optional.of(college.databases));
        when(semesters.findWithContextById(10L)).thenReturn(Optional.of(college.fourth));
        when(offerings.findWithContextById(31L)).thenReturn(Optional.of(college.offeringA));
        when(offerings.saveAndFlush(any(CourseOffering.class))).thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 40L));
    }

    private static void assertRefused(HttpStatus status, String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(status, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void anOfferingJoinsAnExistingCourseAndSemesterLoadedOnTheServer() {
        CourseOfferingResponse created = service.create(new CourseOfferingCreate(13L, 10L));

        assertEquals(new CourseOfferingResponse(40L, 13L, "CS302", "Course CS302", new BigDecimal("3.00"), 10L, 4, 4L,
                "BTECH-CSE", 2L, "2026-27", "PLANNED"), created);
    }

    @Test
    void aMissingCourseOrSemesterIsRefusedAndNothingIsCreated() {
        assertRefused(HttpStatus.NOT_FOUND, CourseOfferingManagementService.COURSE_MISSING,
                () -> service.create(new CourseOfferingCreate(99L, 10L)));
        assertRefused(HttpStatus.NOT_FOUND, CourseOfferingManagementService.SEMESTER_MISSING,
                () -> service.create(new CourseOfferingCreate(13L, 99L)));
        verify(offerings, never()).saveAndFlush(any());
    }

    @Test
    void aCourseIsOfferedAtMostOncePerSemesterEvenWhenTwoRequestsRace() {
        when(offerings.findByCourseIdAndSemesterId(13L, 10L)).thenReturn(Optional.of(college.offeringB));
        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.TAKEN, () -> service.create(new CourseOfferingCreate(13L, 10L)));

        when(offerings.findByCourseIdAndSemesterId(13L, 10L)).thenReturn(Optional.empty());
        when(offerings.saveAndFlush(any(CourseOffering.class))).thenThrow(new DataIntegrityViolationException("uk_course_offerings_course_semester"));
        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.TAKEN, () -> service.create(new CourseOfferingCreate(13L, 10L)));
    }

    @Test
    void theListAndDetailCarryTheOfferingsContextAndTheFiltersReachTheQuery() {
        when(offerings.findAllWithContext(9L, null, 2L, null, Pageable.unpaged())).thenReturn(List.of(college.offeringA, college.offeringB));

        List<CourseOfferingResponse> listed = service.list(9L, null, 2L, null);

        assertEquals(List.of(31L, 32L), listed.stream().map(CourseOfferingResponse::courseOfferingId).toList());
        assertEquals("CS301", service.get(31L).courseCode());
        assertRefused(HttpStatus.NOT_FOUND, CourseOfferingManagementService.NOT_FOUND, () -> service.get(99L));
    }

    @Test
    void onlyTheStatusChangesAndItMovesForwardOnly() {
        assertEquals("ACTIVE", service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.ACTIVE, null, null)).status());
        assertEquals("ACTIVE", service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.ACTIVE, 12L, 9L)).status());
        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.BACK_TO_PLANNED,
                () -> service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.PLANNED, null, null)));
        assertEquals("CLOSED", service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.CLOSED, null, null)).status());
        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.REOPEN,
                () -> service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.ACTIVE, null, null)));
        assertEquals("CLOSED", service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.CLOSED, null, null)).status());
        assertRefused(HttpStatus.NOT_FOUND, CourseOfferingManagementService.NOT_FOUND,
                () -> service.update(99L, new CourseOfferingUpdate(CourseOfferingStatus.ACTIVE, null, null)));
    }

    @Test
    void anOfferingWithEnrollmentsIsNeverMovedToAnotherCourseOrSemester() {
        // Offering A already has a student enrolled; moving it would move that history with it.
        college.courseEnrollment(college.semesterEnrollment(college.studentA, college.third, 88), college.offeringA, 140);

        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.FIXED,
                () -> service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.ACTIVE, 13L, null)));
        assertRefused(HttpStatus.CONFLICT, CourseOfferingManagementService.FIXED,
                () -> service.update(31L, new CourseOfferingUpdate(CourseOfferingStatus.ACTIVE, null, 10L)));

        assertEquals(CourseOfferingStatus.PLANNED, college.offeringA.getStatus());
        assertEquals(12L, college.offeringA.getCourse().getId());
        assertEquals(9L, college.offeringA.getSemester().getId());
        verify(offerings, never()).saveAndFlush(any());
    }

    @Test
    void theCourseAndSemesterColumnsAreNeverWrittenAfterCreation() throws NoSuchFieldException {
        for (String relationship : new String[] {"course", "semester"}) {
            JoinColumn column = CourseOffering.class.getDeclaredField(relationship).getAnnotation(JoinColumn.class);
            assertFalse(column.updatable(), relationship);
        }
    }
}
