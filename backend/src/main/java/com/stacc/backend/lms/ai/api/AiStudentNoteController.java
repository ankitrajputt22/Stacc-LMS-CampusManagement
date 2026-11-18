package com.stacc.backend.lms.ai.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.ai.notes.AiStudentNoteIndexer;
import com.stacc.backend.lms.ai.notes.AiStudentNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lms/courses/{lmsCourseId}/ai/notes")
@Tag(name = "LMS AI study assistant")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course is missing or not open to this student, or the note is "
        + "missing or not theirs. All look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiStudentNoteController {

    private final AiStudentNoteService notes;
    private final AiStudentNoteIndexer indexer;

    public AiStudentNoteController(AiStudentNoteService notes, AiStudentNoteIndexer indexer) {
        this.notes = notes;
        this.indexer = indexer;
    }

    @GetMapping
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "List my notes in the course", description = "The signed-in student's own notes, oldest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The notes.")
    public List<AiStudentNoteResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return notes.list(AuthenticatedAccount.idFrom(accessToken), lmsCourseId).stream().map(AiStudentNoteResponse::of).toList();
    }

    @PostMapping
    @PreAuthorize(AiAccess.STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a note", description = "Plain text up to 10,000 characters; at most 20 notes per course.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new note.")
    @ApiResponse(responseCode = "400", description = "The title or text is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The student already keeps 20 notes in this course.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiStudentNoteResponse create(@PathVariable Long lmsCourseId, @Valid @RequestBody AiStudentNoteRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return AiStudentNoteResponse.of(notes.create(AuthenticatedAccount.idFrom(accessToken), lmsCourseId,
                request.title(), request.content()));
    }

    @PutMapping("/{noteId}")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "Edit a note", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed note.")
    @ApiResponse(responseCode = "400", description = "The title or text is missing or too long.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AiStudentNoteResponse update(@PathVariable Long lmsCourseId, @PathVariable Long noteId,
            @Valid @RequestBody AiStudentNoteRequest request, @AuthenticationPrincipal Jwt accessToken) {
        return AiStudentNoteResponse.of(notes.update(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, noteId,
                request.title(), request.content()));
    }

    @DeleteMapping("/{noteId}")
    @PreAuthorize(AiAccess.STUDENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a note",
            description = "Deletes the note for real, then rebuilds the notes index without it, or removes the index when "
                    + "no note is left. It cannot be undone.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "The note is gone.")
    public void delete(@PathVariable Long lmsCourseId, @PathVariable Long noteId, @AuthenticationPrincipal Jwt accessToken) {
        Long accountId = AuthenticatedAccount.idFrom(accessToken);
        notes.delete(accountId, lmsCourseId, noteId);
        indexer.afterDelete(lmsCourseId, accountId);
    }
}
