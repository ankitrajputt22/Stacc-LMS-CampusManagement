package com.stacc.backend.academic.result.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/results/student")
@Tag(name = "Results")
public class StudentResultController {

    private final StudentResultQueryService queryService;

    public StudentResultController(StudentResultQueryService queryService) {
        this.queryService = queryService;
    }

    // The rule lets in a student account that may view results. Whose results are returned is decided by the query,
    // from the account in the token and that student's own course enrollments.
    @GetMapping("/me")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('ACADEMIC_RESULT_VIEW')")
    @Operation(
            summary = "Get the authenticated student's own published results",
            description = "Returns the signed-in student's published course results, the latest academic session first, "
                    + "then the highest semester number, then course code. Drafts are never included, and a result stays "
                    + "visible after its enrollment ends. The student is taken from the access token, so the request has "
                    + "no parameters.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The student's published results. The list may be empty.")
    @ApiResponse(
            responseCode = "401",
            description = "No valid access token was sent.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(
            responseCode = "403",
            description = "The account is not allowed to use this operation.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public StudentResultsResponse myResults(@AuthenticationPrincipal Jwt accessToken) {
        return queryService.getMyResults(AuthenticatedAccount.idFrom(accessToken));
    }
}
