package com.stacc.backend.support.helpdesk.api;

import java.util.List;

import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.stacc.backend.support.helpdesk.HelpdeskCategoryService;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.CreateHelpdeskCategoryRequest;
import com.stacc.backend.support.helpdesk.HelpdeskRequests.UpdateHelpdeskCategoryRequest;
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskCategoryAdminResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin management of helpdesk categories. Every route needs the admin role and {@code HELPDESK_TICKET_MANAGE}. A
 * category's code is fixed once created, and categories are made inactive instead of deleted.
 */
@RestController
@RequestMapping("/api/helpdesk/admin/categories")
@Tag(name = "Helpdesk admin")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HelpdeskAdminCategoryController {

    private final HelpdeskCategoryService categories;

    public HelpdeskAdminCategoryController(HelpdeskCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "List all helpdesk categories",
            description = "Returns every category, active and inactive, by code.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The categories. The list may be empty.")
    public List<HelpdeskCategoryAdminResponse> list() {
        return categories.allCategories();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Create a helpdesk category",
            description = "Adds a category. The code is stored in uppercase and can never change; it is active unless the request says otherwise.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new category.")
    @ApiResponse(responseCode = "400", description = "A field is missing, too long, or the code has other characters.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "A category with this code already exists.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskCategoryAdminResponse create(@Valid @RequestBody CreateHelpdeskCategoryRequest request) {
        return categories.create(request);
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize(HelpdeskAccess.MANAGE)
    @Operation(summary = "Update a helpdesk category",
            description = "Replaces the name, description, and active flag. The code is not part of the request and never changes. An inactive category keeps its existing tickets but cannot be chosen for new ones.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The updated category.")
    @ApiResponse(responseCode = "400", description = "A field is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The category does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public HelpdeskCategoryAdminResponse update(@PathVariable Long categoryId,
            @Valid @RequestBody UpdateHelpdeskCategoryRequest request) {
        return categories.update(categoryId, request);
    }
}
