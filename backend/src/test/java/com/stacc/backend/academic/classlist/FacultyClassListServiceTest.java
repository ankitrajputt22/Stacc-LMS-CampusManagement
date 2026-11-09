package com.stacc.backend.academic.classlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.stacc.backend.academic.admin.OfferingFacultyLoader;
import com.stacc.backend.academic.enrollment.CourseEnrollmentRepository;
import com.stacc.backend.academic.teaching.FacultyTeachingAccessService;
import com.stacc.backend.academic.teaching.TeachingAssignmentRepository;
import com.stacc.backend.common.error.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FacultyClassListServiceTest {

    private final FacultyTeachingAccessService access = mock(FacultyTeachingAccessService.class);
    private final CourseEnrollmentRepository enrollments = mock(CourseEnrollmentRepository.class);
    private final FacultyClassListService service = new FacultyClassListService(
            mock(TeachingAssignmentRepository.class), access, enrollments, mock(OfferingFacultyLoader.class));

    @Test
    void anOfferingTheCallerDoesNotTeachLooksMissing() {
        when(access.findCurrentTeachingAssignment(anyLong(), anyLong())).thenReturn(Optional.empty());

        ApiException refused = assertThrows(ApiException.class, () -> service.classList(70L, 101L));

        assertEquals(HttpStatus.NOT_FOUND, refused.getStatus());
        assertEquals(FacultyClassListService.UNAVAILABLE, refused.getMessage());
        verifyNoInteractions(enrollments);
    }
}
