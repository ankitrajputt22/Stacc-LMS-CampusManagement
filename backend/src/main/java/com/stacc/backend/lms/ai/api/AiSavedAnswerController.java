package com.stacc.backend.lms.ai.api;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.ai.saved.AiSavedAnswer;
import com.stacc.backend.lms.ai.saved.AiSavedAnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lms/courses/{lmsCourseId}/ai/saved-answers")
@Tag(name = "LMS AI study assistant")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course is missing or not open to this student, or the saved answer is "
        + "missing or not theirs. All look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiSavedAnswerController {

    public record SourceRequest(@NotBlank @Size(max = 30) String sourceType, @NotNull @Positive Long sourceId,
            @Positive Integer pageNumber, @Positive Integer slideNumber) {
    }

    public record SaveRequest(
            @NotBlank @Size(max = AiSavedAnswer.MAX_QUESTION_LENGTH) String question,
            @NotBlank @Size(max = AiSavedAnswer.MAX_ANSWER_LENGTH) String answer,
            @Schema(description = "The sources the answer cited; titles are rebuilt by the server, unknown ones dropped.")
            @Size(max = 12) List<@Valid SourceRequest> sources) {
    }

    public record SavedAnswerResponse(long id, String question, String answer, List<AiCitationResponse> citations, Instant savedAt) {

        static SavedAnswerResponse of(AiSavedAnswer saved) {
            return new SavedAnswerResponse(saved.getId(), saved.getQuestion(), saved.getAnswer(), saved.getSources().stream()
                    .map(source -> new AiCitationResponse(source.getSourceType(), source.getSourceId(), source.getTitle(),
                            source.getFileName(), source.getPageNumber(), source.getSlideNumber(), source.getPageId()))
                    .toList(), saved.getCreatedAt());
        }
    }

    private final AiSavedAnswerService saved;

    public AiSavedAnswerController(AiSavedAnswerService saved) {
        this.saved = saved;
    }

    @GetMapping
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "List my saved answers in the course", description = "Newest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The saved answers.")
    public List<SavedAnswerResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return saved.list(AuthenticatedAccount.idFrom(accessToken), lmsCourseId).stream().map(SavedAnswerResponse::of).toList();
    }

    @PostMapping
    @PreAuthorize(AiAccess.STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save an answer",
            description = "Keeps a question and its AI answer, with the cited sources rebuilt by the server. At most 50 per course.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The saved answer.")
    @ApiResponse(responseCode = "400", description = "The question or answer is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The student already keeps 50 saved answers in this course.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SavedAnswerResponse save(@PathVariable Long lmsCourseId, @Valid @RequestBody SaveRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        List<AiSavedAnswerService.SourceRef> refs = request.sources() == null ? List.of() : request.sources().stream()
                .map(source -> new AiSavedAnswerService.SourceRef(source.sourceType(), source.sourceId(), source.pageNumber(),
                        source.slideNumber()))
                .toList();
        return SavedAnswerResponse.of(saved.save(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request.question(),
                request.answer(), refs));
    }

    @DeleteMapping("/{savedAnswerId}")
    @PreAuthorize(AiAccess.STUDENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a saved answer", description = "Removes it for good. It cannot be undone.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "The saved answer is gone.")
    public void delete(@PathVariable Long lmsCourseId, @PathVariable Long savedAnswerId, @AuthenticationPrincipal Jwt accessToken) {
        saved.delete(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, savedAnswerId);
    }
}
