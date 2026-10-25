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
import com.stacc.backend.support.helpdesk.HelpdeskResponses.HelpdeskCategoryResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The categories a new ticket can be filed under. */
@RestController
@RequestMapping("/api/helpdesk/categories")
@Tag(name = "Helpdesk")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HelpdeskCategoryController {

    private final HelpdeskCategoryService categories;

    public HelpdeskCategoryController(HelpdeskCategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    @PreAuthorize(HelpdeskAccess.CREATE)
    @Operation(
            summary = "List helpdesk categories",
            description = "Returns the active categories, by name, for choosing one on a new ticket. Inactive categories are left out.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The active categories. The list may be empty.")
    public List<HelpdeskCategoryResponse> list() {
        return categories.activeCategories();
    }
}
