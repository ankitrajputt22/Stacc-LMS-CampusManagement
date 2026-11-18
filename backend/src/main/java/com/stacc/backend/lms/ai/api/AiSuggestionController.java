package com.stacc.backend.lms.ai.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "LMS AI teaching tools")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The question or assignment does not exist or is not in a course this "
        + "faculty member teaches. Both look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "409", description = "AI knowledge is not ready for the current content.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "503", description = "AI is switched off or unavailable.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiSuggestionController {

    public record RubricSuggestionRequest(@Schema(description = "From 2 to 8.", example = "4") @NotNull @Min(2) @Max(8) Integer criteriaCount) {
    }

    private final AiTeachingSuggestionService suggestions;

    public AiSuggestionController(AiTeachingSuggestionService suggestions) {
        this.suggestions = suggestions;
    }

    @PostMapping("/api/v1/lms/faculty/ai/questions/{questionId}/model-answer")
    @PreAuthorize(AiAccess.FACULTY_BANK)
    @Operation(summary = "Suggest a model answer to a bank question",
            description = "A model answer with key points and the course sources it comes from. Nothing is saved; the "
                    + "teacher may copy it into the question's marking guidance through the normal edit.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The suggestion, or insufficientContext.")
    public AiModelAnswerResponse modelAnswer(@PathVariable Long questionId, @AuthenticationPrincipal Jwt accessToken) {
        return suggestions.modelAnswer(AuthenticatedAccount.idFrom(accessToken), questionId);
    }

    @PostMapping("/api/v1/lms/faculty/ai/assignments/{assignmentId}/rubric-suggestion")
    @PreAuthorize(AiAccess.FACULTY + " and hasAuthority('LMS_ASSIGNMENT_MANAGE')")
    @Operation(summary = "Suggest a rubric for an assignment",
            description = "Criteria with levels whose maximum points add up to the assignment's maximum marks. Nothing is "
                    + "saved; the rubric form saves it as any rubric written by hand.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The suggestion, or insufficientContext.")
    @ApiResponse(responseCode = "400", description = "criteriaCount is missing or out of range.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiRubricSuggestionResponse rubric(@PathVariable Long assignmentId, @Valid @RequestBody RubricSuggestionRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return suggestions.rubric(AuthenticatedAccount.idFrom(accessToken), assignmentId, request.criteriaCount());
    }
}
