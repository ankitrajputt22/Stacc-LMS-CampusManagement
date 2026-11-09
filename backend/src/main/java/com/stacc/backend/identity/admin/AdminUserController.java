package com.stacc.backend.identity.admin;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.identity.admin.UserAdminRecords.DetailsUpdate;
import com.stacc.backend.identity.admin.UserAdminRecords.FacultyProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.RolesUpdate;
import com.stacc.backend.identity.admin.UserAdminRecords.StatusUpdate;
import com.stacc.backend.identity.admin.UserAdminRecords.StudentProfileCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.TemporaryPasswordIssue;
import com.stacc.backend.identity.admin.UserAdminRecords.UserCreate;
import com.stacc.backend.identity.admin.UserAdminRecords.UserDetail;
import com.stacc.backend.identity.admin.UserAdminRecords.UserPage;
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
 * Admin user management. Reading needs {@code ADMIN} and {@code USER_VIEW}; every change also needs
 * {@code USER_MANAGE}. The account is always named by the route, the admin always comes from the token, and no answer
 * ever carries a password or a hash. There is no delete: accounts are disabled.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin users")
@ApiResponse(responseCode = "401", description = "No valid access token was sent.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account is not allowed to use this operation.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminUserController {

    static final String VIEW = "hasRole('ADMIN') and hasAuthority('USER_VIEW')";
    static final String MANAGE = "hasRole('ADMIN') and hasAuthority('USER_VIEW') and hasAuthority('USER_MANAGE')";

    private final UserAdminService service;
    private final com.stacc.backend.identity.person.PersonPhotoService photos;
    private final UserImportService imports;

    public AdminUserController(UserAdminService service, com.stacc.backend.identity.person.PersonPhotoService photos,
            UserImportService imports) {
        this.service = service;
        this.photos = photos;
        this.imports = imports;
    }

    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Import accounts from a CSV file",
            description = "A UTF-8 CSV file (at most 1 MB and 500 accounts) whose first line is login_id,full_name,email,"
                    + "roles,department,program,admission_session,roll_number. Roles are separated by ;. Every row is "
                    + "checked and its problems listed by line. With dryRun true, or when any row has a problem, nothing is "
                    + "saved; otherwise every account is created in one transaction with the one temporary password sent "
                    + "here, which each person must replace. No password is read from the file.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The rows with their problems, and how many accounts were created.")
    @ApiResponse(responseCode = "400", description = "No usable file, a wrong header, too many rows, or an unusable "
            + "temporary password.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserImportService.ImportResult importAccounts(@AuthenticationPrincipal Jwt token,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestPart("temporaryPassword") String temporaryPassword,
            @RequestParam(defaultValue = "true") boolean dryRun) {
        return imports.importCsv(AuthenticatedAccount.idFrom(token), file, temporaryPassword, dryRun);
    }

    @GetMapping("/{accountId}/photo")
    @PreAuthorize(VIEW)
    @Operation(summary = "An account's official photo", description = "The image, sent inline and never cached.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The JPEG or PNG image.")
    @ApiResponse(responseCode = "404", description = "The account has no photo.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> photo(
            @PathVariable Long accountId) {
        return com.stacc.backend.identity.person.PhotoResponses.of(photos.open(accountId));
    }

    @PostMapping(value = "/{accountId}/photo", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Set the official photo",
            description = "A JPEG or PNG image up to 2 MB, checked by its first bytes and kept in private storage. The "
                    + "previous photo's file is kept. Needs a recorded person profile.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "400", description = "No file, too large, or not a JPEG or PNG image.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The account has no person profile yet.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Photo storage is not available right now.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail uploadPhoto(@AuthenticationPrincipal Jwt token, @PathVariable Long accountId,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file) {
        photos.upload(AuthenticatedAccount.idFrom(token), accountId, file);
        return service.get(accountId);
    }

    @GetMapping
    @PreAuthorize(VIEW)
    @Operation(summary = "Find accounts",
            description = "One page of accounts by login ID, filtered by role and status, and searched by login ID, "
                    + "name, or email. Never a password, a hash, or a phone number.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The page; it may be empty.")
    @ApiResponse(responseCode = "400", description = "A filter is invalid, the text is too long, or the page is out "
            + "of range.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserPage list(
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) AccountStatus status,
            @Parameter(description = "Part of a login ID, name, or email.") @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(role, status, q, page, size);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize(VIEW)
    @Operation(summary = "Get one account", description = "The account with its official name, email, roles, status, "
            + "and student or faculty profile.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail get(@PathVariable Long accountId) {
        return service.get(accountId);
    }

    @PostMapping
    @PreAuthorize(MANAGE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account",
            description = "Creates the account with its official name and email, its roles, a temporary password the "
                    + "person must replace at first sign-in, and the student or faculty profile its roles need, all at "
                    + "once or not at all. There is no public signup.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "201", description = "The new account. The temporary password is never returned.")
    @ApiResponse(responseCode = "400", description = "A field is missing or invalid, the temporary password breaks "
            + "the policy, or a profile is missing for a role.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The login ID, email, or roll number is taken, or the program or "
            + "department is inactive.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail create(@AuthenticationPrincipal Jwt token, @Valid @RequestBody UserCreate request) {
        return service.create(AuthenticatedAccount.idFrom(token), request);
    }

    @PutMapping("/{accountId}/details")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Correct the official name and email",
            description = "The login ID never changes. The person changes only their preferred name and phone.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Another person already has this email.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail updateDetails(@AuthenticationPrincipal Jwt token, @PathVariable Long accountId,
            @Valid @RequestBody DetailsUpdate request) {
        return service.updateDetails(AuthenticatedAccount.idFrom(token), accountId, request);
    }

    @PutMapping("/{accountId}/status")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Activate or disable an account",
            description = "Disabling ends every session of the account at once and keeps all its history. An admin "
                    + "cannot disable their own account.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "An admin tried to disable their own account.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail updateStatus(@AuthenticationPrincipal Jwt token, @PathVariable Long accountId,
            @Valid @RequestBody StatusUpdate request) {
        return service.updateStatus(AuthenticatedAccount.idFrom(token), accountId, request.status());
    }

    @PutMapping("/{accountId}/roles")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Set an account's roles",
            description = "Sets the roles to exactly the ones sent. A real change ends the account's sessions so the "
                    + "new roles apply at the next sign-in. Profiles are kept. An admin cannot remove their own ADMIN "
                    + "role.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "An admin tried to remove their own ADMIN role.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail updateRoles(@AuthenticationPrincipal Jwt token, @PathVariable Long accountId,
            @Valid @RequestBody RolesUpdate request) {
        return service.updateRoles(AuthenticatedAccount.idFrom(token), accountId, request.roles());
    }

    @PostMapping("/{accountId}/temporary-password")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Issue a temporary password",
            description = "Replaces the password with a temporary one that follows the policy, expires after a few "
                    + "days, and only lets the person choose a new password. Every session of the account ends. The "
                    + "password is never returned or logged.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "400", description = "The temporary password breaks the policy.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail issueTemporaryPassword(@AuthenticationPrincipal Jwt token, @PathVariable Long accountId,
            @Valid @RequestBody TemporaryPasswordIssue request) {
        return service.issueTemporaryPassword(AuthenticatedAccount.idFrom(token), accountId, request.temporaryPassword());
    }

    @PostMapping("/{accountId}/student-profile")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Create the student profile",
            description = "For an account holding STUDENT without a student profile: program, admission session, and "
                    + "an optional roll number.", security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The account lacks STUDENT, already has a profile, the program is "
            + "inactive, or the roll number is taken.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail addStudentProfile(@AuthenticationPrincipal Jwt token, @PathVariable Long accountId,
            @Valid @RequestBody StudentProfileCreate request) {
        return service.addStudentProfile(AuthenticatedAccount.idFrom(token), accountId, request);
    }

    @PostMapping("/{accountId}/faculty-profile")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Create the faculty profile",
            description = "For an account holding FACULTY without a faculty profile: the primary department.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The account.")
    @ApiResponse(responseCode = "404", description = "The account does not exist.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The account lacks FACULTY, already has a profile, or the "
            + "department is inactive.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public UserDetail addFacultyProfile(@AuthenticationPrincipal Jwt token, @PathVariable Long accountId,
            @Valid @RequestBody FacultyProfileCreate request) {
        return service.addFacultyProfile(AuthenticatedAccount.idFrom(token), accountId, request);
    }
}
