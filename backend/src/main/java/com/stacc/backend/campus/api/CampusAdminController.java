package com.stacc.backend.campus.api;

import java.util.List;

import com.stacc.backend.approval.ApprovalActor;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.campus.services.CampusServiceDefinitionService;
import com.stacc.backend.campus.services.CampusServiceInputs.CreateCampusServiceInput;
import com.stacc.backend.campus.services.CampusServiceInputs.UpdateCampusServiceInput;
import com.stacc.backend.campus.services.CampusServiceQueryService;
import com.stacc.backend.campus.services.CampusServiceRequestService;
import com.stacc.backend.campus.services.CampusServiceRequestStatus;
import com.stacc.backend.campus.services.CampusServiceResponses.AdminCampusRequestDetail;
import com.stacc.backend.campus.services.CampusServiceResponses.AdminCampusRequestPage;
import com.stacc.backend.campus.services.CampusServiceResponses.CampusServiceDefinitionResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The campus office's service catalog and service requests. Every route needs the admin role and
 * CAMPUS_SERVICE_MANAGE. There are no delete routes, and no approve or reject routes: the office decides approvals with
 * the approval task routes and the step shown in the request detail, which check the step's role and
 * APPROVAL_TASK_DECIDE.
 */
@RestController
@RequestMapping("/api/campus/admin")
@Tag(name = "Campus admin")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class CampusAdminController {

    private final CampusServiceDefinitionService services;
    private final CampusServiceRequestService requests;
    private final CampusServiceQueryService queries;

    public CampusAdminController(CampusServiceDefinitionService services, CampusServiceRequestService requests,
            CampusServiceQueryService queries) {
        this.services = services;
        this.requests = requests;
        this.queries = queries;
    }

    @GetMapping("/services")
    @PreAuthorize(CampusAccess.MANAGE_SERVICES)
    @Operation(summary = "List campus services", description = "Every service, active or not, by code, with its full configuration.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The services. The list may be empty.")
    public List<CampusServiceDefinitionResponse> services() {
        return services.list();
    }

    @GetMapping("/services/{serviceId}")
    @PreAuthorize(CampusAccess.MANAGE_SERVICES)
    @Operation(summary = "Get one campus service", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The service.")
    @ApiResponse(responseCode = "404", description = "The service does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusServiceDefinitionResponse service(@PathVariable Long serviceId) {
        return services.get(serviceId);
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CampusAccess.MANAGE_SERVICES)
    @Operation(summary = "Add a campus service",
            description = "Adds a service with a unique code that never changes. It is offered to students, faculty, or both. When approval is required, the approval workflow named by its code must exist and be active.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new service.")
    @ApiResponse(responseCode = "400", description = "A field is missing or not valid, or the service is offered to no role.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The code is taken, or the approval workflow does not exist or is inactive.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusServiceDefinitionResponse create(@Valid @RequestBody CreateCampusServiceInput input) {
        return services.create(input);
    }

    @PutMapping("/services/{serviceId}")
    @PreAuthorize(CampusAccess.MANAGE_SERVICES)
    @Operation(summary = "Update a campus service",
            description = "Replaces everything but the code. An inactive service takes no new requests; its requests stay. Approval settings affect future requests only.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The service.")
    @ApiResponse(responseCode = "400", description = "A field is missing or not valid, or the service is offered to no role.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The service does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The newly chosen approval workflow does not exist or is inactive.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public CampusServiceDefinitionResponse update(@PathVariable Long serviceId,
            @Valid @RequestBody UpdateCampusServiceInput input) {
        return services.update(serviceId, input);
    }

    @GetMapping("/requests")
    @PreAuthorize(CampusAccess.MANAGE_SERVICES)
    @Operation(summary = "List campus service requests",
            description = "One page of every request, newest first, with its status and approval status. Each filter that is sent narrows the list; the status filter matches the status shown.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "One page of requests. The list may be empty.")
    @ApiResponse(responseCode = "400", description = "A filter is not valid, the page is below 0, or the size is not between 1 and 100.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusRequestPage requests(
            @Parameter(description = "Only requests for this service.") @RequestParam(required = false) Long serviceId,
            @Parameter(description = "SUBMITTED, APPROVED, REJECTED, CANCELLED, or COMPLETED.") @RequestParam(required = false) CampusServiceRequestStatus status,
            @Parameter(description = "Only this requester's requests.") @RequestParam(required = false) String requesterLoginId,
            @Parameter(description = "The page number, from 0.") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "How many per page, from 1 to 100.") @RequestParam(defaultValue = "20") int size) {
        return queries.adminRequests(serviceId, status, requesterLoginId, page, size);
    }

    @GetMapping("/requests/{requestId}")
    @PreAuthorize(CampusAccess.MANAGE_SERVICES)
    @Operation(summary = "Get one campus service request",
            description = "The request with its approval steps and history, the step waiting for a decision with whether the signed-in admin may decide it through the approval task routes, and whether it can be completed.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The request.")
    @ApiResponse(responseCode = "404", description = "The request does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusRequestDetail request(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long requestId) {
        return queries.adminRequest(actor(accessToken, authentication), requestId);
    }

    @PostMapping("/requests/{requestId}/complete")
    @PreAuthorize(CampusAccess.MANAGE_SERVICES)
    @Operation(summary = "Complete a campus service request",
            description = "Marks an approved request completed, records when, and tells the requester in their Inbox. Completing a completed request changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The request, now completed.")
    @ApiResponse(responseCode = "404", description = "The request does not exist.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The request is still waiting for approval, or was rejected or cancelled.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminCampusRequestDetail complete(@AuthenticationPrincipal Jwt accessToken, Authentication authentication,
            @PathVariable Long requestId) {
        return requests.complete(actor(accessToken, authentication), requestId);
    }

    private static ApprovalActor actor(Jwt accessToken, Authentication authentication) {
        return ApprovalActor.of(AuthenticatedAccount.idFrom(accessToken), authentication.getAuthorities());
    }
}
