package com.stacc.backend.academic.timetable.api;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.stacc.backend.academic.timetable.ClassTimetableSlot;
import com.stacc.backend.academic.timetable.TimetableService;
import com.stacc.backend.academic.timetable.TimetableService.MyTimetable;
import com.stacc.backend.academic.timetable.TimetableService.SlotView;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The class timetable: one's own week, and the admin routes that keep each semester's slots. */
@RestController
@Tag(name = "Timetable")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class TimetableController {

    private static final String OWN = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('TIMETABLE_VIEW_OWN')";
    private static final String VIEW = "hasRole('ADMIN') and hasAuthority('ACADEMIC_TIMETABLE_VIEW')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('ACADEMIC_TIMETABLE_VIEW') and hasAuthority('ACADEMIC_TIMETABLE_MANAGE')";

    private final TimetableService service;

    public TimetableController(TimetableService service) {
        this.service = service;
    }

    public record SlotRequest(@NotNull Long courseOfferingId, @NotNull DayOfWeek day, @NotNull LocalTime startsAt,
            @NotNull LocalTime endsAt, @NotNull @Size(max = 40) String room, @NotNull ClassTimetableSlot.Kind kind) {
    }

    @GetMapping("/api/v1/timetable/me")
    @PreAuthorize(OWN)
    @Operation(summary = "The signed-in account's weekly timetable",
            description = "Slots of the offerings the student is enrolled in now (studying) and those the faculty member "
                    + "teaches now (teaching); a part is null when the account does not hold that role.")
    public MyTimetable mine(org.springframework.security.core.Authentication authentication) {
        Jwt token = (Jwt) authentication.getPrincipal();
        return service.mine(AuthenticatedAccount.idFrom(token), holds(authentication, "ROLE_STUDENT"), holds(authentication, "ROLE_FACULTY"));
    }

    private static boolean holds(org.springframework.security.core.Authentication authentication, String authority) {
        return authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }

    @GetMapping("/api/v1/admin/academic/timetable")
    @PreAuthorize(VIEW)
    @Operation(summary = "A semester's timetable", description = "The slots in use of every offering of the semester.")
    public List<SlotView> semester(@RequestParam Long semesterId) {
        return service.forSemester(semesterId);
    }

    @PostMapping("/api/v1/admin/academic/timetable")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a weekly class", description = "Refuses with 409 a room or a class of students booked twice at once.")
    @ApiResponse(responseCode = "409", description = "A clash, or a closed offering or semester.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public SlotView add(@Valid @RequestBody SlotRequest request) {
        return service.add(request.courseOfferingId(), request.day(), request.startsAt(), request.endsAt(), request.room(), request.kind());
    }

    @PostMapping("/api/v1/admin/academic/timetable/{slotId}/remove")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a weekly class", description = "The slot is kept as history; removing it again changes nothing.")
    public void remove(@PathVariable Long slotId) {
        service.remove(slotId);
    }
}
