package com.stacc.backend.academic.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.academic.admin.FacultyAssignmentRequests.FacultyAssignmentCreate;
import com.stacc.backend.academic.admin.FacultyAssignmentRequests.FacultyAssignmentUpdate;
import com.stacc.backend.academic.offering.CourseOfferingRepository;
import com.stacc.backend.academic.teaching.TeachingAssignment;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.academic.teaching.TeachingRole;
import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.role.Role;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.faculty.FacultyProfile;
import com.stacc.backend.identity.faculty.FacultyProfileRepository;
import com.stacc.backend.identity.person.PersonProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.http.HttpStatus;

class FacultyAssignmentServiceTest {

    private final CourseOfferingRepository offerings = mock(CourseOfferingRepository.class);
    private final FacultyProfileRepository facultyProfiles = mock(FacultyProfileRepository.class);
    private final TeachingAssignmentRepository assignments = mock(TeachingAssignmentRepository.class);
    private final PersonProfileRepository people = mock(PersonProfileRepository.class);
    private final FacultyAssignmentService service = new FacultyAssignmentService(offerings, facultyProfiles, assignments,
            new OfferingFacultyLoader(assignments, people));
    private final EnrollmentFixtures college = new EnrollmentFixtures();
    private FacultyProfile faculty;

    @BeforeEach
    void setUp() {
        faculty = faculty(11, 81, "faculty1", RoleName.FACULTY);
        when(offerings.findByIdForUpdate(31L)).thenReturn(Optional.of(college.offeringA));
        when(facultyProfiles.findWithAccountById(11L)).thenReturn(Optional.of(faculty));
        when(assignments.saveAndFlush(any(TeachingAssignment.class)))
                .thenAnswer(call -> MasterFixtures.saved(call.getArgument(0), 7L));
        when(assignments.findWithFacultyById(7L)).thenAnswer(call -> Optional.ofNullable(saved));
        when(people.findAllByUserAccountIdIn(any())).thenReturn(List.of());
    }

    private TeachingAssignment saved;

    private FacultyProfile faculty(long profileId, long accountId, String loginId, RoleName role) {
        UserAccount account = MasterFixtures.withId(new UserAccount(loginId, "example-hash-value"), accountId);
        account.assignRole(new Role(role));
        return MasterFixtures.withId(new FacultyProfile(account, college.cse), profileId);
    }

    private static void assertConflict(String message, Executable action) {
        ApiException refused = assertThrows(ApiException.class, action);
        assertEquals(HttpStatus.CONFLICT, refused.getStatus());
        assertEquals(message, refused.getMessage());
    }

    @Test
    void aFacultyMemberIsAssignedWithTheirRole() {
        when(assignments.saveAndFlush(any(TeachingAssignment.class))).thenAnswer(call -> {
            saved = MasterFixtures.saved(call.getArgument(0), 7L);
            return saved;
        });

        var assigned = service.assign(new FacultyAssignmentCreate(31L, 11L, TeachingRole.CO_FACULTY));

        assertEquals("faculty1", assigned.loginId());
        assertEquals("CO_FACULTY", assigned.role());
        assertEquals("ACTIVE", assigned.status());
    }

    @Test
    void aSecondPrimaryOrADuplicateAssignmentIsRefused() {
        when(assignments.existsByCourseOfferingIdAndRoleAndStatusAndIdNot(eq(31L), eq(TeachingRole.PRIMARY),
                eq(TeachingAssignmentStatus.ACTIVE), anyLong())).thenReturn(true);
        assertConflict(FacultyAssignmentService.PRIMARY_TAKEN,
                () -> service.assign(new FacultyAssignmentCreate(31L, 11L, TeachingRole.PRIMARY)));

        TeachingAssignment active = MasterFixtures.withId(new TeachingAssignment(faculty, college.offeringA), 7L);
        when(assignments.findByFacultyProfileIdAndCourseOfferingId(11L, 31L)).thenReturn(Optional.of(active));
        assertConflict(FacultyAssignmentService.ALREADY_ASSIGNED,
                () -> service.assign(new FacultyAssignmentCreate(31L, 11L, TeachingRole.CO_FACULTY)));
        verify(assignments, never()).saveAndFlush(any());
    }

    @Test
    void onlyActiveFacultyAccountsCanBeAssigned() {
        faculty.getUserAccount().setStatus(AccountStatus.DISABLED);
        assertConflict(FacultyAssignmentService.FACULTY_INACTIVE,
                () -> service.assign(new FacultyAssignmentCreate(31L, 11L, TeachingRole.PRIMARY)));

        FacultyProfile student = faculty(12, 82, "student9", RoleName.STUDENT);
        when(facultyProfiles.findWithAccountById(12L)).thenReturn(Optional.of(student));
        assertConflict(FacultyAssignmentService.NOT_FACULTY,
                () -> service.assign(new FacultyAssignmentCreate(31L, 12L, TeachingRole.PRIMARY)));
    }

    @Test
    void aClosedOfferingsTeachingCannotChange() {
        college.offeringA.markActive();
        college.offeringA.close();

        assertConflict(FacultyAssignmentService.OFFERING_CLOSED,
                () -> service.assign(new FacultyAssignmentCreate(31L, 11L, TeachingRole.PRIMARY)));
    }

    @Test
    void anInactiveAssignmentIsMadeActiveAgainInsteadOfDuplicated() {
        TeachingAssignment old = MasterFixtures.withId(new TeachingAssignment(faculty, college.offeringA), 7L);
        old.deactivate();
        saved = old;
        when(assignments.findByFacultyProfileIdAndCourseOfferingId(11L, 31L)).thenReturn(Optional.of(old));
        when(assignments.saveAndFlush(old)).thenReturn(old);

        var reassigned = service.assign(new FacultyAssignmentCreate(31L, 11L, TeachingRole.PRIMARY));

        assertEquals(7L, reassigned.teachingAssignmentId());
        assertEquals("ACTIVE", reassigned.status());
        assertEquals("PRIMARY", reassigned.role());
    }

    @Test
    void anAssignmentIsDeactivatedAndKept() {
        TeachingAssignment active = MasterFixtures.withId(new TeachingAssignment(faculty, college.offeringA), 7L);
        when(assignments.findWithFacultyById(7L)).thenReturn(Optional.of(active));

        var changed = service.update(7L, new FacultyAssignmentUpdate(TeachingRole.PRIMARY, TeachingAssignmentStatus.INACTIVE));

        assertEquals("INACTIVE", changed.status());
        verify(assignments).saveAndFlush(active);
    }
}
