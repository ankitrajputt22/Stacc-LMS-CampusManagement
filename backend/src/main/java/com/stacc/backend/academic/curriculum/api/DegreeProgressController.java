package com.stacc.backend.academic.curriculum.api;

import java.util.List;

import com.stacc.backend.academic.curriculum.DegreeProgressService;
import com.stacc.backend.academic.curriculum.DegreeProgressService.DegreeProgress;
import com.stacc.backend.academic.curriculum.DegreeProgressService.Requirement;
import com.stacc.backend.academic.result.AcademicStandingService;
import com.stacc.backend.academic.result.AcademicStandingService.Standing;
import com.stacc.backend.academic.result.GradeScale;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Degree progress, a student's standing (SGPA and CGPA), the grade scale, and the admin routes for requirements. */
@RestController
@Tag(name = "Degree progress and standing")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class DegreeProgressController {

    private final DegreeProgressService progress;
    private final AcademicStandingService standing;

    public DegreeProgressController(DegreeProgressService progress, AcademicStandingService standing) {
        this.progress = progress;
        this.standing = standing;
    }

    public record RequirementsRequest(@NotNull @Size(max = 5) List<@NotNull @Valid Requirement> requirements) {
    }

    @GetMapping("/api/v1/academic/me/degree-progress")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('ACADEMIC_PROFILE_VIEW_OWN')")
    @Operation(summary = "The signed-in student's degree progress",
            description = "Credits earned per required course type from passed, published results, worked out on read.")
    @ApiResponse(responseCode = "404", description = "No student profile is recorded for this account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public DegreeProgress mine(@AuthenticationPrincipal Jwt accessToken) {
        return progress.mine(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/api/v1/results/student/me/standing")
    @PreAuthorize("hasRole('STUDENT') and hasAuthority('ACADEMIC_RESULT_VIEW')")
    @Operation(summary = "The signed-in student's grades, SGPA, and CGPA",
            description = "From published results and the college's grade scale, credit-weighted and worked out on read.")
    public Standing myStanding(@AuthenticationPrincipal Jwt accessToken) {
        return standing.standing(AuthenticatedAccount.idFrom(accessToken));
    }

    @GetMapping("/api/v1/academic/grade-scale")
    @PreAuthorize("hasAnyAuthority('ACADEMIC_RESULT_VIEW', 'ACADEMIC_RESULT_MANAGE', 'ACADEMIC_MASTER_VIEW')")
    @Operation(summary = "The college's grade scale", description = "Grades best first, with their points and lowest percentages.")
    public List<GradeScale.Grade> gradeScale() {
        return standing.scale().grades();
    }

    @GetMapping("/api/v1/admin/academic/programs/{programId}/credit-requirements")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACADEMIC_MASTER_VIEW')")
    @Operation(summary = "A program's degree requirements")
    public List<Requirement> requirements(@PathVariable Long programId) {
        return progress.requirementsOf(programId);
    }

    @PutMapping("/api/v1/admin/academic/programs/{programId}/credit-requirements")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACADEMIC_MASTER_VIEW') and hasAuthority('ACADEMIC_MASTER_MANAGE')")
    @Operation(summary = "Set a program's degree requirements",
            description = "Each listed course type is added or changed; requirements are never removed.")
    public List<Requirement> setRequirements(@PathVariable Long programId, @Valid @RequestBody RequirementsRequest request) {
        return progress.set(programId, request.requirements());
    }
}
