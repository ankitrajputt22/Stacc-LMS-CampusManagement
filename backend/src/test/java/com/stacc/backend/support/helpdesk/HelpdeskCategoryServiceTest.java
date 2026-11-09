package com.stacc.backend.support.helpdesk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskCategoryRequest;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.UpdateHelpdeskCategoryRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskCategoryAdminResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class HelpdeskCategoryServiceTest {

    private final HelpdeskCategoryRepository categories = mock(HelpdeskCategoryRepository.class);
    private final HelpdeskCategoryService service = new HelpdeskCategoryService(categories);

    private static void assertRefused(Runnable call, HttpStatus status, String message) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, refusal -> {
            assertThat(refusal.getStatus()).isEqualTo(status);
            assertThat(refusal.getMessage()).isEqualTo(message);
        });
    }

    @Test
    void requestersGetOnlyActiveCategories() {
        when(categories.findAllByActiveTrueOrderByNameAscIdAsc())
                .thenReturn(List.of(HelpdeskFixtures.category(3, "LMS", true)));

        assertThat(service.activeCategories()).singleElement()
                .satisfies(category -> assertThat(category.code()).isEqualTo("LMS"));
        verify(categories, never()).findAllByOrderByCodeAscIdAsc();
    }

    @Test
    void adminsGetEveryCategoryWithItsActiveFlag() {
        when(categories.findAllByOrderByCodeAscIdAsc()).thenReturn(List.of(HelpdeskFixtures.category(3, "LMS", true),
                HelpdeskFixtures.category(4, "OLD", false)));

        assertThat(service.allCategories()).extracting(HelpdeskCategoryAdminResponse::active).containsExactly(true, false);
    }

    @Test
    void creatingStoresTheCodeInUppercaseAndActiveByDefault() {
        when(categories.saveAndFlush(any())).thenAnswer(call -> HelpdeskFixtures.withId(call.getArgument(0), 9));

        HelpdeskCategoryAdminResponse created = service.create(
                new CreateHelpdeskCategoryRequest(" hostel ", "Hostel", "Rooms and facilities.", null));

        assertThat(created.categoryId()).isEqualTo(9);
        assertThat(created.code()).isEqualTo("HOSTEL");
        assertThat(created.active()).isTrue();
        verify(categories).existsByCode("HOSTEL");
    }

    @Test
    void aCategoryCanBeCreatedInactive() {
        when(categories.saveAndFlush(any())).thenAnswer(call -> HelpdeskFixtures.withId(call.getArgument(0), 9));

        assertThat(service.create(new CreateHelpdeskCategoryRequest("LATER", "Later", null, false)).active()).isFalse();
    }

    @Test
    void aTakenOrInvalidCodeIsRefused() {
        when(categories.existsByCode("LMS")).thenReturn(true);
        assertRefused(() -> service.create(new CreateHelpdeskCategoryRequest("lms", "LMS", null, true)),
                HttpStatus.CONFLICT, HelpdeskCategoryService.CODE_TAKEN);
        assertRefused(() -> service.create(new CreateHelpdeskCategoryRequest("1-LMS", "LMS", null, true)),
                HttpStatus.BAD_REQUEST, HelpdeskCategoryService.BAD_CODE);
        verify(categories, never()).saveAndFlush(any());
    }

    @Test
    void aRaceForTheSameCodeAnswers409WithoutDatabaseDetails() {
        when(categories.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_helpdesk_categories_code"));

        assertRefused(() -> service.create(new CreateHelpdeskCategoryRequest("LMS", "LMS", null, true)),
                HttpStatus.CONFLICT, HelpdeskCategoryService.CODE_TAKEN);
    }

    @Test
    void updatingChangesNameDescriptionAndActiveButNeverTheCode() {
        HelpdeskCategory lms = HelpdeskFixtures.category(3, "LMS", true);
        when(categories.findById(3L)).thenReturn(Optional.of(lms));
        when(categories.saveAndFlush(lms)).thenReturn(lms);

        HelpdeskCategoryAdminResponse updated = service.update(3L,
                new UpdateHelpdeskCategoryRequest("LMS and courses", "Access and content.", false));

        assertThat(updated.code()).isEqualTo("LMS");
        assertThat(updated.name()).isEqualTo("LMS and courses");
        assertThat(updated.description()).isEqualTo("Access and content.");
        assertThat(updated.active()).isFalse();
    }

    @Test
    void updatingAMissingCategoryIs404() {
        assertRefused(() -> service.update(404L, new UpdateHelpdeskCategoryRequest("Name", null, true)),
                HttpStatus.NOT_FOUND, HelpdeskCategoryService.NOT_FOUND);
    }
}
