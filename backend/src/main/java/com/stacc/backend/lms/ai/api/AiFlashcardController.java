package com.stacc.backend.lms.ai.api;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.lms.ai.flashcards.AiFlashcard;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardSet;
import com.stacc.backend.lms.ai.flashcards.AiFlashcardStore;
import com.stacc.backend.lms.ai.flashcards.ReviewRating;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

/** A student's flashcard sets in a course they may use now (v0.15.3). Nobody else has a route to them. */
@RestController
@RequestMapping("/api/v1/lms/courses/{lmsCourseId}/ai/flashcard-sets")
@Tag(name = "LMS AI study assistant")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "The course is missing or not open to this student, or the set or card is "
        + "missing or not theirs. All look the same.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AiFlashcardController {

    public record SetSummaryResponse(long id, String title, long cards, long dueToday) {
    }

    public record CardResponse(long id, int position, String front, String back, AiCitationResponse source, LocalDate dueOn,
            int intervalDays, int repetitions) {

        static CardResponse of(AiFlashcard card) {
            return new CardResponse(card.getId(), card.getPosition(), card.getFront(), card.getBack(),
                    new AiCitationResponse(card.getSourceType(), card.getSourceId(), card.getSourceTitle(), null, null, null,
                            card.getPageId()), card.getDueOn(), card.getIntervalDays(), card.getRepetitions());
        }
    }

    /** {@code today} is the college's date, so the student's review shows the cards the server counts as due. */
    public record SetResponse(long id, String title, List<CardResponse> cards, LocalDate today) {

        static SetResponse of(AiFlashcardSet set, LocalDate today) {
            return new SetResponse(set.getId(), set.getTitle(), set.getCards().stream().map(CardResponse::of).toList(), today);
        }
    }

    public record ReviewRequest(@NotNull ReviewRating rating) {
    }

    private final AiFlashcardService flashcards;

    public AiFlashcardController(AiFlashcardService flashcards) {
        this.flashcards = flashcards;
    }

    @GetMapping
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "List my flashcard sets", description = "Newest first, with how many cards are due today.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The sets.")
    public List<SetSummaryResponse> list(@PathVariable Long lmsCourseId, @AuthenticationPrincipal Jwt accessToken) {
        return flashcards.list(AuthenticatedAccount.idFrom(accessToken), lmsCourseId).stream()
                .map((AiFlashcardStore.SetSummary summary) -> new SetSummaryResponse(summary.set().getId(), summary.set().getTitle(),
                        summary.cards(), summary.due()))
                .toList();
    }

    @PostMapping
    @PreAuthorize(AiAccess.STUDENT)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Make a flashcard set with AI",
            description = "Cards from a published page, module, file, or the whole course, each with its source. At most 20 sets.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new set.")
    @ApiResponse(responseCode = "409", description = "AI knowledge is not ready, AI made no usable card, the assistant is off, "
            + "or the student already keeps 20 sets.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "AI is switched off or unavailable.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SetResponse create(@PathVariable Long lmsCourseId, @Valid @RequestBody AiFlashcardSetRequest request,
            @AuthenticationPrincipal Jwt accessToken) {
        return SetResponse.of(flashcards.create(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, request), flashcards.today());
    }

    @GetMapping("/{setId}")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "Open a flashcard set", description = "Every card with its source and when it is next due.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The set.")
    public SetResponse get(@PathVariable Long lmsCourseId, @PathVariable Long setId, @AuthenticationPrincipal Jwt accessToken) {
        return SetResponse.of(flashcards.get(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, setId), flashcards.today());
    }

    @PostMapping("/{setId}/cards/{cardId}/review")
    @PreAuthorize(AiAccess.STUDENT)
    @Operation(summary = "Review a card",
            description = "AGAIN, HARD, GOOD, or EASY; the next due day is worked out by the spacing rule. Needs no AI.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The card with its next due day.")
    public CardResponse review(@PathVariable Long lmsCourseId, @PathVariable Long setId, @PathVariable Long cardId,
            @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt accessToken) {
        return CardResponse.of(flashcards.review(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, setId, cardId, request.rating()));
    }

    @DeleteMapping("/{setId}")
    @PreAuthorize(AiAccess.STUDENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a flashcard set", description = "Removes the set and its cards for good.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "The set is gone.")
    public void delete(@PathVariable Long lmsCourseId, @PathVariable Long setId, @AuthenticationPrincipal Jwt accessToken) {
        flashcards.delete(AuthenticatedAccount.idFrom(accessToken), lmsCourseId, setId);
    }
}
