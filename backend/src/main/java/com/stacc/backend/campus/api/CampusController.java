package com.stacc.backend.campus.api;

import java.util.List;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.campus.CampusActor;
import com.stacc.backend.campus.notices.CampusNoticeQueryService;
import com.stacc.backend.campus.notices.CampusNoticeResponses.CampusNoticePage;
import com.stacc.backend.campus.services.CampusServiceDefinitionService;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceRequestInput;
import com.stacc.backend.campus.services.CampusServiceQueryService;
import com.stacc.backend.campus.services.CampusServiceRequestService;
import com.stacc.backend.campus.services.CampusServiceRequestStatus;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusRequestDetail;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusRequestPage;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusServiceSummary;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The common campus routes: the services a student or faculty member may request, their own requests, and the notice
 * feed. The caller and every role it holds always come from the access token; no route takes an account or requester
 * ID. A service request has no conversation: support questions belong to the helpdesk.
 */
@RestController
@RequestMapping("/api/campus")
@Tag(name = "Campus")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class CampusController {

    private static final String NOT_FOUND = "The request does not exist, or belongs to another account.";

    private final CampusServiceDefinitionService services;
    private final CampusServiceRequestService requests;
    private final CampusServiceQueryService queries;
    private final CampusNoticeQueryService notices;

    public CampusController(CampusServiceDefinitionService services, CampusServiceRequestService requests,
            CampusServiceQueryService queries, CampusNoticeQueryService notices) {
        this.services = services;
        this.requests = requests;
        this.queries = queries;
        this.notices = notices;
    }

    @GetMapping("/services")
    @PreAuthorize(CampusAccess.REQUEST)
    @Operation(summary = "List campus services I may request",
            description = "The active services offered to any role the signed-in account holds (STUDENT, FACULTY, or both), each once, by name.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The services. The list may be empty.")
    public List<CampusServiceSummary> services(@AuthenticationPrincipal Jwt accessToken, Authentication authentication) {
        return services.available(actor(accessToken, authentication));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CampusAccess.REQUEST)
    @Operation(summary = "Request a campus service",
            description = "Creates a request with a subject and plain-text details for an active service available to the caller. It gets a number such as CSR-000001. When the service needs approval the request starts SUBMITTED and its approval starts in the approval engine; otherwise it starts APPROVED, accepted for handling.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new request.")
    @ApiResponse(responseCode = "400", description = "The service, subject, or details are missing or too long.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The service does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The service is inactive, is not available to the caller's roles, or cannot start its approval.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusRequestDetail create(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Valid @RequestBody CreateCampusServiceRequestInput input) {
        return requests.create(actor(accessToken, authentication), input);
    }

    @GetMapping("/requests/me")
    @PreAuthorize(CampusAccess.VIEW_OWN)
    @Operation(summary = "List my service requests",
            description = "One page of the signed-in account's requests, newest first. The status filter matches the request status shown, with the approval's outcome taken into account.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of requests. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "A filter is not valid, the page is below 0, or the size is not between 1 and 100.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusRequestPage mine(@AuthenticationPrincipal Jwt accessToken,
            @Parameter(description = "SUBMITTED, APPROVED, REJECTED, CANCELLED, or COMPLETED.") @RequestParam(required = false) CampusServiceRequestStatus status,
            @Parameter(description = "Only requests for this service.") @RequestParam(required = false) Long serviceId,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.ownRequests(AuthenticatedAccount.idFrom(accessToken), status, serviceId, page, size);
    }

    @GetMapping("/requests/{requestId}")
    @PreAuthorize(CampusAccess.VIEW_OWN)
    @Operation(summary = "Get one of my service requests",
            description = "The request with its approval progress when its service needs approval, and the approver's comment if it was rejected.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The request.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusRequestDetail request(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long requestId) {
        return queries.ownRequest(AuthenticatedAccount.idFrom(accessToken), requestId);
    }

    @PostMapping("/requests/{requestId}/cancel")
    @PreAuthorize(CampusAccess.CANCEL)
    @Operation(summary = "Cancel my service request",
            description = "Cancels the caller's own request while it is submitted or approved. A pending approval is cancelled in the approval engine.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The request, now cancelled.")
    @ApiResponse(responseCode = "404", description = NOT_FOUND, content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The request is completed, rejected, or already cancelled.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusRequestDetail cancel(@AuthenticationPrincipal Jwt accessToken, @PathVariable Long requestId) {
        return requests.cancel(AuthenticatedAccount.idFrom(accessToken), requestId);
    }

    @GetMapping("/notices")
    @PreAuthorize(CampusAccess.VIEW_NOTICES)
    @Operation(summary = "List campus notices",
            description = "One page of the published notices visible to any role the signed-in account holds, newest first, each once.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of notices. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "The page is below 0, or the size is not between 1 and 100.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusNoticePage notices(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return notices.feed(actor(accessToken, authentication), page, size);
    }

    private static CampusActor actor(Jwt accessToken, Authentication authentication) {
        return CampusActor.of(AuthenticatedAccount.idFrom(accessToken), authentication.getAuthorities());
    }
}
