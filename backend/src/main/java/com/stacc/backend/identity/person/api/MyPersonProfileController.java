package com.stacc.backend.identity.person.api;

import com.stacc.backend.auth.security.AuthenticatedAccount;
import com.stacc.backend.common.config.OpenApiConfig;
import com.stacc.backend.common.error.ApiErrorResponse;
import com.stacc.backend.identity.person.PersonProfileService;
import com.stacc.backend.identity.person.api.PersonProfileRequests.ChangeMyPersonalDetailsRequest;
import com.stacc.backend.identity.person.PersonProfileResponses.MyPersonProfile;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/profile")
@Tag(name = "My profile")
@ApiResponse(responseCode = "401", description = "Not signed in.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "The account may not use its profile.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class MyPersonProfileController {

    static final String VIEW = "hasAuthority('PERSON_PROFILE_VIEW_OWN')";
    static final String EDIT = "hasAuthority('PERSON_PROFILE_EDIT_OWN')";

    private final PersonProfileService profiles;
    private final com.stacc.backend.identity.person.PersonPhotoService photos;

    public MyPersonProfileController(PersonProfileService profiles,
            com.stacc.backend.identity.person.PersonPhotoService photos) {
        this.profiles = profiles;
        this.photos = photos;
    }

    @GetMapping("/photo")
    @PreAuthorize(VIEW)
    @Operation(summary = "The signed-in person's official photo",
            description = "The image the college added, sent inline and never cached by shared caches.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The JPEG or PNG image.")
    @ApiResponse(responseCode = "404", description = "No photo has been added.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "503", description = "Photo storage is not available right now.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public org.springframework.http.ResponseEntity<org.springframework.core.io.InputStreamResource> myPhoto(
            @AuthenticationPrincipal Jwt accessToken) {
        return com.stacc.backend.identity.person.PhotoResponses.of(photos.open(AuthenticatedAccount.idFrom(accessToken)));
    }

    @GetMapping
    @PreAuthorize(VIEW)
    @Operation(summary = "Read the signed-in person's profile",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The profile, or the login ID alone while none is recorded.")
    public MyPersonProfile myProfile(@AuthenticationPrincipal Jwt accessToken) {
        return profiles.myProfile(AuthenticatedAccount.idFrom(accessToken));
    }

    @PutMapping
    @PreAuthorize(EDIT)
    @Operation(summary = "Change the signed-in person's preferred name and phone number",
            description = "The college sets the full name, email address, and photo; only these two change here.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed profile.")
    @ApiResponse(responseCode = "400", description = "A value is not valid.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The college has not recorded the profile yet.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MyPersonProfile changeMyPersonalDetails(
            @AuthenticationPrincipal Jwt accessToken, @Valid @RequestBody ChangeMyPersonalDetailsRequest request) {
        return profiles.changeMyPersonalDetails(AuthenticatedAccount.idFrom(accessToken), request.preferredName(),
                request.phone());
    }

    public record MailingAddressRequest(@jakarta.validation.constraints.Size(max = 500) String mailingAddress) {
    }

    public record EmergencyContactRequest(
            @jakarta.validation.constraints.NotBlank(message = "Name is required") @jakarta.validation.constraints.Size(max = 150) String name,
            @jakarta.validation.constraints.NotBlank(message = "Relation is required") @jakarta.validation.constraints.Size(max = 50) String relation,
            @jakarta.validation.constraints.NotBlank(message = "Phone number is required") @jakarta.validation.constraints.Size(max = 20) String phone) {
    }

    @PutMapping("/mailing-address")
    @PreAuthorize(EDIT)
    @Operation(summary = "Change the signed-in person's mailing address",
            description = "Plain text up to 500 characters with line breaks kept; blank clears it.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed profile.")
    public MyPersonProfile changeMyMailingAddress(@AuthenticationPrincipal Jwt accessToken,
            @Valid @RequestBody MailingAddressRequest request) {
        return profiles.changeMyMailingAddress(AuthenticatedAccount.idFrom(accessToken), request.mailingAddress());
    }

    @org.springframework.web.bind.annotation.PostMapping("/emergency-contacts")
    @PreAuthorize(EDIT)
    @Operation(summary = "Add an emergency contact", description = "At most three; a fourth answers 409.",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed profile.")
    @ApiResponse(responseCode = "409", description = "Three contacts already, or no profile recorded yet.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MyPersonProfile addMyEmergencyContact(@AuthenticationPrincipal Jwt accessToken,
            @Valid @RequestBody EmergencyContactRequest request) {
        return profiles.addMyEmergencyContact(AuthenticatedAccount.idFrom(accessToken), request.name(), request.relation(),
                request.phone());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/emergency-contacts/{contactId}")
    @PreAuthorize(EDIT)
    @Operation(summary = "Remove one of the signed-in person's emergency contacts",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH))
    @ApiResponse(responseCode = "200", description = "The changed profile.")
    @ApiResponse(responseCode = "404", description = "No such contact of the signed-in person.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public MyPersonProfile removeMyEmergencyContact(@AuthenticationPrincipal Jwt accessToken,
            @org.springframework.web.bind.annotation.PathVariable Long contactId) {
        return profiles.removeMyEmergencyContact(AuthenticatedAccount.idFrom(accessToken), contactId);
    }
}
