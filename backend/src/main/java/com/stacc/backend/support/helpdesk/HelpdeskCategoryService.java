package com.stacc.backend.support.helpdesk;

import java.util.List;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskCategoryRequest;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.UpdateHelpdeskCategoryRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskCategoryAdminResponse;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskCategoryResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helpdesk categories: the active ones for requesters, and all of them for admins, who add and edit them. A code is
 * set once. A category is made inactive rather than deleted, so its old tickets keep it.
 */
@Service
@Transactional
public class HelpdeskCategoryService {

    static final String NOT_FOUND = "Category not found.";
    static final String CODE_TAKEN = "A category with this code already exists.";
    static final String BAD_CODE = "The code must be letters, digits, and underscores, starting with a letter.";

    private final HelpdeskCategoryRepository categories;

    public HelpdeskCategoryService(HelpdeskCategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<HelpdeskCategoryResponse> activeCategories() {
        return categories.findAllByActiveTrueOrderByNameAscIdAsc().stream().map(HelpdeskCategoryResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<HelpdeskCategoryAdminResponse> allCategories() {
        return categories.findAllByOrderByCodeAscIdAsc().stream().map(HelpdeskCategoryAdminResponse::of).toList();
    }

    public HelpdeskCategoryAdminResponse create(CreateHelpdeskCategoryRequest request) {
        String code;
        try {
            code = HelpdeskCategory.normalizedCode(request.code());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_CODE);
        }
        if (categories.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, CODE_TAKEN);
        }
        HelpdeskCategory category = new HelpdeskCategory(code, request.name(), request.description());
        if (Boolean.FALSE.equals(request.active())) {
            category.update(category.getName(), category.getDescription(), false);
        }
        try {
            return HelpdeskCategoryAdminResponse.of(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException clash) {
            // Two requests raced for the same code; the unique constraint refused the second.
            throw new ApiException(HttpStatus.CONFLICT, CODE_TAKEN);
        }
    }

    /** Changes the name, description, and whether new tickets may use it. The code never changes. */
    public HelpdeskCategoryAdminResponse update(Long categoryId, UpdateHelpdeskCategoryRequest request) {
        HelpdeskCategory category = categories.findById(categoryId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, NOT_FOUND));
        category.update(request.name(), request.description(), request.active());
        return HelpdeskCategoryAdminResponse.of(categories.saveAndFlush(category));
    }
}
