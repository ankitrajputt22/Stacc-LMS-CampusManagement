package com.stacc.backend.hostel.api;

import java.time.LocalDate;
import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.hostel.mess.MealFeedback;
import com.stacc.backend.hostel.mess.Meal;
import com.stacc.backend.hostel.mess.MessMenuItem;
import com.stacc.backend.hostel.mess.MessService;
import com.stacc.backend.hostel.mess.MessService.Manager;
import com.stacc.backend.hostel.mess.MessService.ManagerMess;
import com.stacc.backend.hostel.mess.MessService.RatedMeal;
import com.stacc.backend.hostel.mess.MessService.ResidentMess;
import com.stacc.backend.hostel.mess.MessService.TemplateLine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The hostel mess: residents read the week and rate meals; the hostel office and wardens manage menus and read feedback. */
@RestController
@Tag(name = "Hostel mess")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class HostelMessController {

    private static final String MANAGE = "hasAnyRole('ADMIN', 'WARDEN') and hasAuthority('HOSTEL_MESS_MANAGE')";
    private static final String PATH = "/api/v1/hostel/mess/manage";

    private final MessService service;

    public HostelMessController(MessService service) {
        this.service = service;
    }

    public record RateRequest(@NotNull LocalDate date, @NotNull Meal meal, @Min(1) @Max(5) int rating,
            @Size(max = MealFeedback.MAX_COMMENT_LENGTH) String comment) {
    }

    public record TemplateLineRequest(@Min(1) @Max(7) int dayOfWeek, @NotNull Meal meal,
            @NotBlank @Size(max = MessMenuItem.MAX_ITEMS_LENGTH) String items) {
    }

    public record TemplateRequest(@NotNull Long hostelId, @NotNull @Size(min = 1, max = 28) List<@NotNull @Valid TemplateLineRequest> lines) {
    }

    public record ChangeRequest(@NotNull Long hostelId, @NotNull LocalDate date, @NotNull Meal meal,
            @NotBlank @Size(max = MessMenuItem.MAX_ITEMS_LENGTH) String items, @Size(max = 200) String note) {
    }

    private static Manager manager(Jwt token, Authentication authentication) {
        boolean office = authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return new Manager(AuthenticatedAccount.idFrom(token), office);
    }

    @GetMapping("/api/v1/hostel/student/mess")
    @PreAuthorize(HostelAccess.VIEW_OWN)
    @Operation(summary = "My hostel's mess week", description = "Seven days from the date (today when empty), with my ratings of recent meals.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The week; without a hostel bed, no hostel name and an empty week.")
    public ResidentMess week(@RequestParam(required = false) LocalDate from, @AuthenticationPrincipal Jwt token) {
        return service.residentWeek(AuthenticatedAccount.idFrom(token), from);
    }

    @PostMapping("/api/v1/hostel/student/mess/feedback")
    @PreAuthorize(HostelAccess.VIEW_OWN)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Rate a meal", description = "A meal of today or the last seven days at my hostel, once, from 1 to 5.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "409", description = "Already rated, or no hostel bed.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public RatedMeal rate(@Valid @RequestBody RateRequest request, @AuthenticationPrincipal Jwt token) {
        return service.rate(AuthenticatedAccount.idFrom(token), request.date(), request.meal(), request.rating(), request.comment());
    }

    @GetMapping(PATH)
    @PreAuthorize(MANAGE)
    @Operation(summary = "A hostel's mess", description = "Its weekly lines, the coming week with day changes, and the last month's feedback without names; averages only for at least five ratings.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "404", description = "Not a hostel the account manages.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ManagerMess forManager(@RequestParam Long hostelId, @RequestParam(required = false) LocalDate from,
            @AuthenticationPrincipal Jwt token, Authentication authentication) {
        return service.forManager(manager(token, authentication), hostelId, from);
    }

    @PutMapping(PATH + "/template")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Set weekly menu lines", description = "Each line is a day of the week (1 Monday to 7 Sunday) and a meal; lines not sent stay.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "Saved.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setTemplate(@Valid @RequestBody TemplateRequest request, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        service.setTemplate(manager(token, authentication), request.hostelId(),
                request.lines().stream().map(line -> new TemplateLine(line.dayOfWeek(), line.meal(), line.items())).toList());
    }

    @PutMapping(PATH + "/changes")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change one meal on one date", description = "Replaces the weekly line for that date only.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "204", description = "Saved.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setChange(@Valid @RequestBody ChangeRequest request, @AuthenticationPrincipal Jwt token, Authentication authentication) {
        service.setChange(manager(token, authentication), request.hostelId(), request.date(), request.meal(), request.items(), request.note());
    }
}
