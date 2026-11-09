package com.stacc.backend.engagement.api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.engagement.events.CampusEventService;
import com.stacc.backend.engagement.events.CampusEventService.AttendeeView;
import com.stacc.backend.engagement.events.CampusEventService.EventInput;
import com.stacc.backend.engagement.events.CampusEventService.EventView;
import com.stacc.backend.engagement.events.CampusEventService.Viewer;
import com.stacc.backend.engagement.facilities.CampusFacility;
import com.stacc.backend.engagement.facilities.CampusFacilityService;
import com.stacc.backend.engagement.facilities.CampusFacilityService.BookingView;
import com.stacc.backend.engagement.facilities.CampusFacilityService.FacilityView;
import com.stacc.backend.engagement.groups.CampusGroup;
import com.stacc.backend.engagement.groups.CampusGroupOfficer;
import com.stacc.backend.engagement.groups.CampusGroupService;
import com.stacc.backend.engagement.groups.CampusGroupService.GroupView;
import com.stacc.backend.engagement.groups.CampusGroupService.MemberView;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Campus engagement: groups and memberships, facilities and bookings, and events with registration. People use them with
 * CAMPUS_ENGAGEMENT_USE (students and faculty); admins set them up with CAMPUS_ENGAGEMENT_MANAGE. Every check of who may
 * do what per record (officers, organizers, bookers) happens in the services.
 */
@RestController
@RequestMapping("/api/v1/engagement")
@Tag(name = "Campus engagement")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class EngagementController {

    private static final String USE = "hasAnyRole('STUDENT', 'FACULTY') and hasAuthority('CAMPUS_ENGAGEMENT_USE')";
    private static final String MANAGE = "hasRole('ADMIN') and hasAuthority('CAMPUS_ENGAGEMENT_MANAGE')";
    private static final String ANY = "(" + USE + ") or (" + MANAGE + ")";

    public record GroupRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name, @NotNull CampusGroup.Kind kind,
            @Size(max = 2000) String description, @NotNull CampusGroup.JoinPolicy joinPolicy) {
    }

    public record GroupUpdateRequest(@NotBlank @Size(max = 150) String name, @NotNull CampusGroup.Kind kind, @Size(max = 2000) String description,
            @NotNull CampusGroup.JoinPolicy joinPolicy, boolean active) {
    }

    public record OfficerRequest(@NotBlank @Size(max = 100) String loginId, @NotNull CampusGroupOfficer.Title title) {
    }

    public record DecisionRequest(boolean accept) {
    }

    public record FacilityRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name, @NotNull CampusFacility.Kind kind,
            @Size(max = 200) String location, Integer capacity, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt, boolean needsApproval) {
    }

    public record FacilityUpdateRequest(@NotBlank @Size(max = 150) String name, @NotNull CampusFacility.Kind kind, @Size(max = 200) String location,
            Integer capacity, @NotNull LocalTime opensAt, @NotNull LocalTime closesAt, boolean needsApproval, boolean active) {
    }

    public record ClosureRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotBlank @Size(max = 500) String reason) {
    }

    public record BookingRequest(@NotNull Instant startsAt, @NotNull Instant endsAt, @NotBlank @Size(max = 500) String purpose) {
    }

    public record EventRequest(@NotBlank @Size(max = 200) String title, @Size(max = 10_000) String description, Long groupId,
            @Size(max = 200) String venue, @NotNull Instant startsAt, @NotNull Instant endsAt, Integer capacity, Instant registrationOpensAt,
            Instant registrationClosesAt, boolean forStudents, boolean forFaculty, boolean membersOnly) {

        EventInput input() {
            return new EventInput(title, description, groupId, venue, startsAt, endsAt, capacity, registrationOpensAt, registrationClosesAt,
                    forStudents, forFaculty, membersOnly);
        }
    }

    private final CampusGroupService groups;
    private final CampusFacilityService facilities;
    private final CampusEventService events;

    public EngagementController(CampusGroupService groups, CampusFacilityService facilities, CampusEventService events) {
        this.groups = groups;
        this.facilities = facilities;
        this.events = events;
    }

    // --- Groups

    @GetMapping("/groups")
    @PreAuthorize(ANY)
    @Operation(summary = "List clubs and sports groups", description = "Active groups with members counted, officers, and your membership. Admins also see inactive ones.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<GroupView> groups(@AuthenticationPrincipal Jwt token, Authentication authentication) {
        return groups.list(AuthenticatedAccount.idFrom(token), viewer(token, authentication).admin());
    }

    @PostMapping("/groups/{groupId}/join")
    @PreAuthorize(USE)
    @Operation(summary = "Join a group", description = "At once for an open group; a request for one that needs approval.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public GroupView join(@AuthenticationPrincipal Jwt token, @PathVariable Long groupId) {
        return groups.join(AuthenticatedAccount.idFrom(token), groupId);
    }

    @PostMapping("/groups/{groupId}/leave")
    @PreAuthorize(USE)
    @Operation(summary = "Leave a group or withdraw a request to join", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public GroupView leave(@AuthenticationPrincipal Jwt token, @PathVariable Long groupId) {
        return groups.leave(AuthenticatedAccount.idFrom(token), groupId);
    }

    @GetMapping("/groups/{groupId}/members")
    @PreAuthorize(ANY)
    @Operation(summary = "A group's members and requests", description = "For its officers and admins; others get 404.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<MemberView> members(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long groupId) {
        return groups.members(AuthenticatedAccount.idFrom(token), viewer(token, authentication).admin(), groupId);
    }

    @PostMapping("/memberships/{membershipId}/decision")
    @PreAuthorize(ANY)
    @Operation(summary = "Accept or refuse a request to join", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<MemberView> decide(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long membershipId,
            @RequestBody DecisionRequest request) {
        return groups.decide(AuthenticatedAccount.idFrom(token), viewer(token, authentication).admin(), membershipId, request.accept());
    }

    @PostMapping("/admin/groups")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a group", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public GroupView createGroup(@Valid @RequestBody GroupRequest request) {
        return groups.create(request.code(), request.name(), request.kind(), request.description(), request.joinPolicy());
    }

    @PutMapping("/admin/groups/{groupId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a group or make it inactive", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public GroupView updateGroup(@PathVariable Long groupId, @Valid @RequestBody GroupUpdateRequest request) {
        return groups.update(groupId, request.name(), request.kind(), request.description(), request.joinPolicy(), request.active());
    }

    @PostMapping("/admin/groups/{groupId}/officers")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Name an officer", description = "An advisor or coach must hold FACULTY.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public GroupView addOfficer(@PathVariable Long groupId, @Valid @RequestBody OfficerRequest request) {
        return groups.addOfficer(groupId, request.loginId(), request.title());
    }

    @PostMapping("/admin/officers/{officerId}/end")
    @PreAuthorize(MANAGE)
    @Operation(summary = "End an officer's term", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public GroupView endOfficer(@PathVariable Long officerId) {
        return groups.endOfficer(officerId);
    }

    // --- Facilities

    @GetMapping("/facilities")
    @PreAuthorize(ANY)
    @Operation(summary = "List facilities", description = "With opening hours and coming closures.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<FacilityView> facilities(@AuthenticationPrincipal Jwt token, Authentication authentication) {
        return facilities.list(viewer(token, authentication).admin());
    }

    @GetMapping("/facilities/{facilityId}/bookings")
    @PreAuthorize(ANY)
    @Operation(summary = "A facility's coming bookings", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<BookingView> facilityBookings(@PathVariable Long facilityId) {
        return facilities.upcoming(facilityId);
    }

    @PostMapping("/facilities/{facilityId}/bookings")
    @PreAuthorize(USE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a facility", description = "Within one day's opening hours, at most 12 hours, never overlapping another booking.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public BookingView book(@AuthenticationPrincipal Jwt token, @PathVariable Long facilityId, @Valid @RequestBody BookingRequest request) {
        return facilities.book(AuthenticatedAccount.idFrom(token), facilityId, request.startsAt(), request.endsAt(), request.purpose());
    }

    @GetMapping("/bookings/mine")
    @PreAuthorize(USE)
    @Operation(summary = "Your bookings", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<BookingView> myBookings(@AuthenticationPrincipal Jwt token) {
        return facilities.mine(AuthenticatedAccount.idFrom(token));
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    @PreAuthorize(USE)
    @Operation(summary = "Cancel your booking before it starts", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public BookingView cancelBooking(@AuthenticationPrincipal Jwt token, @PathVariable Long bookingId) {
        return facilities.cancel(AuthenticatedAccount.idFrom(token), bookingId);
    }

    @PostMapping("/admin/facilities")
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a facility", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FacilityView createFacility(@Valid @RequestBody FacilityRequest request) {
        return facilities.create(request.code(), request.name(), request.kind(), request.location(), request.capacity(), request.opensAt(),
                request.closesAt(), request.needsApproval());
    }

    @PutMapping("/admin/facilities/{facilityId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change a facility or make it inactive", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FacilityView updateFacility(@PathVariable Long facilityId, @Valid @RequestBody FacilityUpdateRequest request) {
        return facilities.update(facilityId, request.name(), request.kind(), request.location(), request.capacity(), request.opensAt(),
                request.closesAt(), request.needsApproval(), request.active());
    }

    @PostMapping("/admin/facilities/{facilityId}/closures")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Close a facility for some days", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public FacilityView close(@PathVariable Long facilityId, @Valid @RequestBody ClosureRequest request) {
        return facilities.close(facilityId, request.startDate(), request.endDate(), request.reason());
    }

    // --- Events

    @GetMapping("/events")
    @PreAuthorize(ANY)
    @Operation(summary = "Coming events", description = "Published and cancelled events that have not ended, with your registration and why you cannot register.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<EventView> events(@AuthenticationPrincipal Jwt token, Authentication authentication) {
        return events.upcoming(viewer(token, authentication));
    }

    @GetMapping("/events/managed")
    @PreAuthorize(ANY)
    @Operation(summary = "Events you organize", description = "Yours and your groups'; every event for admins.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<EventView> managed(@AuthenticationPrincipal Jwt token, Authentication authentication) {
        return events.managed(viewer(token, authentication));
    }

    @GetMapping("/events/{eventId}")
    @PreAuthorize(ANY)
    @Operation(summary = "One event", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventView event(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long eventId) {
        return events.one(viewer(token, authentication), eventId);
    }

    @GetMapping("/events/{eventId}/attendees")
    @PreAuthorize(ANY)
    @Operation(summary = "An event's registrations and waitlist", description = "For its organizers and admins.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public List<AttendeeView> attendees(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long eventId) {
        return events.attendees(viewer(token, authentication), eventId);
    }

    @PostMapping("/events")
    @PreAuthorize(ANY)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a draft event", description = "Group officers for their group; admins for any.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventView create(@AuthenticationPrincipal Jwt token, Authentication authentication, @Valid @RequestBody EventRequest request) {
        return events.create(viewer(token, authentication), request.input());
    }

    @PutMapping("/events/{eventId}")
    @PreAuthorize(ANY)
    @Operation(summary = "Edit a draft event", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventView edit(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long eventId,
            @Valid @RequestBody EventRequest request) {
        return events.edit(viewer(token, authentication), eventId, request.input());
    }

    @PostMapping("/events/{eventId}/publish")
    @PreAuthorize(ANY)
    @Operation(summary = "Publish a draft", description = "A group event goes to its faculty advisor for approval first.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventView publish(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long eventId) {
        return events.publish(viewer(token, authentication), eventId);
    }

    @PostMapping("/events/{eventId}/cancel")
    @PreAuthorize(ANY)
    @Operation(summary = "Cancel an event", description = "It stays visible as cancelled.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventView cancel(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long eventId) {
        return events.cancel(viewer(token, authentication), eventId);
    }

    @PostMapping("/events/{eventId}/registration")
    @PreAuthorize(USE)
    @Operation(summary = "Register for an event", description = "A full event puts you on the waitlist.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventView register(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long eventId) {
        return events.register(viewer(token, authentication), eventId);
    }

    @PostMapping("/events/{eventId}/registration/cancel")
    @PreAuthorize(USE)
    @Operation(summary = "Give up your place or leave the waitlist", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    public EventView unregister(@AuthenticationPrincipal Jwt token, Authentication authentication, @PathVariable Long eventId) {
        return events.unregister(viewer(token, authentication), eventId);
    }

    private static Viewer viewer(Jwt token, Authentication authentication) {
        Set<String> held = AuthorityUtils.authorityListToSet(authentication.getAuthorities());
        return new Viewer(AuthenticatedAccount.idFrom(token), held.contains("ROLE_STUDENT"), held.contains("ROLE_FACULTY"),
                held.contains("ROLE_ADMIN") && held.contains("CAMPUS_ENGAGEMENT_MANAGE"));
    }
}
