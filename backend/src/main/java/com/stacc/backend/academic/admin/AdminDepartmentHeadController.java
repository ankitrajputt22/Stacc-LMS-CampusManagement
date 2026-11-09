package com.stacc.backend.academic.admin;

import java.util.List;

import com.stacc.backend.academic.admin.DepartmentHeadRecords.AssignDepartmentHead;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.DepartmentHeadRow;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.HeadCandidate;
import com.stacc.backend.academic.admin.DepartmentHeadRecords.HeadTerm;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who heads each department, managed by admins with the department head permissions. */
@RestController
@RequestMapping("/api/v1/admin/academic")
@Tag(name = "Admin department heads")
@ApiResponse(
        responseCode = "401",
        description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(
        responseCode = "403",
        description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminDepartmentHeadController {

    private final DepartmentHeadService service;

    public AdminDepartmentHeadController(DepartmentHeadService service) {
        this.service = service;
    }

    @GetMapping("/department-heads")
    @PreAuthorize(AdminAcademicAccess.DEPARTMENT_HEAD_VIEW)
    @Operation(summary = "Department heads",
            description = "Every department by code, with its current head or none, and whether that head counts now.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The departments. The list may be empty.")
    public List<DepartmentHeadRow> overview() {
        return service.overview();
    }

    @GetMapping("/department-heads/candidates")
    @PreAuthorize(AdminAcademicAccess.DEPARTMENT_HEAD_VIEW)
    @Operation(summary = "Who can head a department",
            description = "Active accounts holding HOD, by login ID: account ID, login ID, and name only.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The accounts. The list may be empty.")
    public List<HeadCandidate> candidates() {
        return service.candidates();
    }

    @GetMapping("/departments/{departmentId}/heads")
    @PreAuthorize(AdminAcademicAccess.DEPARTMENT_HEAD_VIEW)
    @Operation(summary = "A department's heads over time", description = "Every term, the latest first.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The terms. The list may be empty.")
    @ApiResponse(responseCode = "404", description = "The department does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<HeadTerm> history(@PathVariable Long departmentId) {
        return service.history(departmentId);
    }

    @PutMapping("/departments/{departmentId}/head")
    @PreAuthorize(AdminAcademicAccess.DEPARTMENT_HEAD_MANAGE)
    @Operation(summary = "Set a department's head",
            description = "Ends the current head's term, if any, and starts a new one. The same head again changes nothing.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The department with its head.")
    @ApiResponse(responseCode = "404", description = "The department or account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The department is inactive, or the account is disabled or does "
            + "not hold HOD.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DepartmentHeadRow assign(@AuthenticationPrincipal Jwt token, @PathVariable Long departmentId,
            @Valid @RequestBody AssignDepartmentHead request) {
        return service.assign(AuthenticatedAccount.idFrom(token), departmentId, request);
    }

    @PostMapping("/departments/{departmentId}/head/end")
    @PreAuthorize(AdminAcademicAccess.DEPARTMENT_HEAD_MANAGE)
    @Operation(summary = "End a department head's term",
            description = "The term is kept as history. Without a current head nothing changes.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The department, now without a head.")
    @ApiResponse(responseCode = "404", description = "The department does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DepartmentHeadRow end(@AuthenticationPrincipal Jwt token, @PathVariable Long departmentId) {
        return service.end(AuthenticatedAccount.idFrom(token), departmentId);
    }
}
