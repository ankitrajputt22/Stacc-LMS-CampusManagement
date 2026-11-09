package com.stacc.backend.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.calendar.personal.CalendarFeedService;
import com.stacc.backend.calendar.personal.PersonalCalendarEvent;
import com.stacc.backend.calendar.personal.PersonalCalendarService;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** A person's own calendar entries and private subscription link, and the public feed that link opens. */
@RestController
@RequestMapping("/api/v1/calendar")
@Tag(name = "Personal calendar")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account cannot use a personal calendar.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class PersonalCalendarController {

    private static final String ALLOWED =
            "hasAnyRole('STUDENT', 'FACULTY', 'ADMIN') and hasAuthority('ACADEMIC_CALENDAR_VIEW') and hasAuthority('CALENDAR_PERSONAL_USE')";

    @Schema(description = "A personal entry: a title, optional notes, and dates (end included).")
    public record EventRequest(@NotBlank @Size(max = PersonalCalendarEvent.MAX_TITLE_LENGTH) String title,
            @Size(max = PersonalCalendarEvent.MAX_NOTES_LENGTH) String notes, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    }

    @Schema(description = "A personal entry, seen only by its owner.")
    public record EventResponse(Long eventId, String title, String notes, LocalDate startDate, LocalDate endDate) {

        static EventResponse of(PersonalCalendarEvent e) {
            return new EventResponse(e.getId(), e.getTitle(), e.getNotes(), e.getStartDate(), e.getEndDate());
        }
    }

    @Schema(description = "Whether a subscription link is active, and since when.")
    public record FeedStatusResponse(boolean active, Instant createdAt) {
    }

    @Schema(description = "A new subscription link's path. It contains the secret, is shown only now, and can be revoked.")
    public record NewFeedResponse(String feedPath, Instant createdAt) {
    }

    private final PersonalCalendarService personal;
    private final CalendarFeedService feeds;

    public PersonalCalendarController(PersonalCalendarService personal, CalendarFeedService feeds) {
        this.personal = personal;
        this.feeds = feeds;
    }

    @GetMapping("/me/personal-events")
    @PreAuthorize(ALLOWED)
    @Operation(summary = "List your personal entries in a date range", description = "At most 366 days; entries overlapping the range.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<EventResponse> list(@AuthenticationPrincipal Jwt token,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || from.plusDays(366).isBefore(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a range of at most 366 days, ending after it starts.");
        }
        return personal.overlapping(AuthenticatedAccount.idFrom(token), from, to).stream().map(EventResponse::of).toList();
    }

    @PostMapping("/me/personal-events")
    @PreAuthorize(ALLOWED)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a personal entry", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventResponse create(@AuthenticationPrincipal Jwt token, @Valid @RequestBody EventRequest request) {
        return EventResponse.of(personal.create(AuthenticatedAccount.idFrom(token), request.title(), request.notes(),
                request.startDate(), request.endDate()));
    }

    @PutMapping("/me/personal-events/{eventId}")
    @PreAuthorize(ALLOWED)
    @Operation(summary = "Change one of your personal entries", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "404", description = "No such entry of yours.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public EventResponse change(@AuthenticationPrincipal Jwt token, @PathVariable Long eventId, @Valid @RequestBody EventRequest request) {
        return EventResponse.of(personal.change(AuthenticatedAccount.idFrom(token), eventId, request.title(), request.notes(),
                request.startDate(), request.endDate()));
    }

    @DeleteMapping("/me/personal-events/{eventId}")
    @PreAuthorize(ALLOWED)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove one of your personal entries", description = "Personal entries are your own notes and are removed for good.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void delete(@AuthenticationPrincipal Jwt token, @PathVariable Long eventId) {
        personal.delete(AuthenticatedAccount.idFrom(token), eventId);
    }

    @GetMapping("/me/feed")
    @PreAuthorize(ALLOWED)
    @Operation(summary = "Whether your subscription link is active", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FeedStatusResponse feedStatus(@AuthenticationPrincipal Jwt token) {
        PersonalCalendarService.FeedStatus status = personal.feedStatus(AuthenticatedAccount.idFrom(token));
        return new FeedStatusResponse(status.active(), status.createdAt());
    }

    @PostMapping("/me/feed")
    @PreAuthorize(ALLOWED)
    @Operation(summary = "Make a new subscription link",
            description = "Revokes any earlier link. The returned path holds the secret and is shown only once.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public NewFeedResponse createFeed(@AuthenticationPrincipal Jwt token) {
        PersonalCalendarService.NewFeed feed = personal.createFeed(AuthenticatedAccount.idFrom(token));
        return new NewFeedResponse("/api/v1/calendar/feeds/" + feed.token() + ".ics", feed.createdAt());
    }

    @PostMapping("/me/feed/revoke")
    @PreAuthorize(ALLOWED)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke your subscription link", description = "Calendar apps using it get nothing from now on.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public void revokeFeed(@AuthenticationPrincipal Jwt token) {
        personal.revokeFeed(AuthenticatedAccount.idFrom(token));
    }

    /**
     * Public on purpose (listed in SecurityConfig): calendar apps cannot sign in. The secret in the path only reads the
     * calendar of the account behind an active link; a wrong, revoked, or disabled one answers the same 404.
     */
    @GetMapping(value = "/feeds/{token}.ics", produces = "text/calendar")
    @Operation(summary = "Read a calendar through its private subscription link", description = "iCalendar text, read only.")
    @ApiResponse(responseCode = "404", description = "The link is unknown or revoked.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<String> feed(@PathVariable String token) {
        String body = feeds.feed(token).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Calendar not found."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
