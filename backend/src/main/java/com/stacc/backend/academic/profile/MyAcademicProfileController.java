package com.stacc.backend.academic.profile;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in student's or faculty member's own official academic profile, read-only. */
@RestController
@RequestMapping("/api/v1/academic/me")
@Tag(name = "My academic profile")
public class MyAcademicProfileController {

    private final MyAcademicProfileService service;

    public MyAcademicProfileController(MyAcademicProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ACADEMIC_PROFILE_VIEW_OWN') and hasAnyRole('STUDENT', 'FACULTY')")
    @Operation(summary = "My academic profile",
            description = "A part for each of the student and faculty roles the account holds: the program, department, "
                    + "admission session, where the student studies now, and their semester-by-semester record with "
                    + "published results; the faculty member's department and the offerings they teach. The account "
                    + "comes from the access token.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The profile; a part is null when it does not apply.")
    @ApiResponse(responseCode = "403", description = "The account holds neither role or lacks the permission.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MyAcademicProfileResponse profile(Authentication authentication) {
        Jwt token = (Jwt) authentication.getPrincipal();
        boolean asStudent = holds(authentication, "ROLE_STUDENT");
        boolean asFaculty = holds(authentication, "ROLE_FACULTY");
        return service.profile(AuthenticatedAccount.idFrom(token), asStudent, asFaculty);
    }

    private static boolean holds(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(authority::equals);
    }
}
