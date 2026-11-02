package com.stacc.backend.identity.admin;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.stacc.backend.auth.account.AccountStatus;
import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.password.PasswordPolicy;
import com.stacc.backend.auth.role.RoleName;
import com.stacc.backend.identity.person.PersonProfileValues;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Requests and answers of admin user management. No answer ever carries a password or a hash, and no request names
 * the account in its body: the route does.
 */
public final class UserAdminRecords {

    private UserAdminRecords() {
    }

    /** A new account with its official name, roles, temporary password, and the profiles its roles need. */
    public record UserCreate(
            @Schema(description = "The student or employee ID the person signs in with. Unique and never changed "
                    + "afterwards.", example = "25CSE104")
            @NotBlank(message = "Login ID is required")
            @Size(max = UserAccount.MAX_LOGIN_ID_LENGTH, message = "Login ID must be at most 100 characters")
            String loginId,

            @Schema(description = "The official full name.", example = "Riya Menon")
            @NotBlank(message = "Full name is required")
            @Size(max = PersonProfileValues.MAX_FULL_NAME, message = "Full name must be at most 150 characters")
            String fullName,

            @Schema(description = "The official email, optional and unique.", example = "riya.menon@college.example")
            @Size(max = PersonProfileValues.MAX_EMAIL, message = "Email must be at most 254 characters")
            String email,

            @Schema(description = "At least one of STUDENT, FACULTY, and ADMIN.")
            @NotEmpty(message = "Choose at least one role")
            Set<@NotNull RoleName> roles,

            @Schema(description = "A temporary password for the first sign-in. It must follow the password policy, "
                    + "expires after a few days, and only lets the person choose a new password.")
            @NotBlank(message = "Temporary password is required")
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                    message = "Temporary password must be 10 to 128 characters")
            String temporaryPassword,

            @Schema(description = "Needed when the roles include STUDENT; otherwise ignored.")
            @Valid
            StudentProfileCreate studentProfile,

            @Schema(description = "Needed when the roles include FACULTY; otherwise ignored.")
            @Valid
            FacultyProfileCreate facultyProfile) {
    }

    public record StudentProfileCreate(
            @Schema(example = "4") @NotNull(message = "Program is required") Long programId,
            @Schema(description = "The session of original admission.", example = "2")
            @NotNull(message = "Admission session is required") Long admissionSessionId,
            @Schema(description = "Optional, stored in uppercase, unique.", example = "25CSE104")
            @Size(max = 30, message = "Roll number must be at most 30 characters") String rollNumber) {
    }

    public record FacultyProfileCreate(
            @Schema(description = "The primary department. It never limits what the person teaches.", example = "3")
            @NotNull(message = "Department is required") Long departmentId) {
    }

    public record DetailsUpdate(
            @Schema(example = "Riya Menon")
            @NotBlank(message = "Full name is required")
            @Size(max = PersonProfileValues.MAX_FULL_NAME, message = "Full name must be at most 150 characters")
            String fullName,
            @Schema(description = "Optional; empty clears it.", example = "riya.menon@college.example")
            @Size(max = PersonProfileValues.MAX_EMAIL, message = "Email must be at most 254 characters")
            String email) {
    }

    public record StatusUpdate(
            @Schema(allowableValues = {"ACTIVE", "DISABLED"}) @NotNull(message = "Status is required")
            AccountStatus status) {
    }

    public record RolesUpdate(
            @Schema(description = "The complete set of roles the account should hold.")
            @NotEmpty(message = "Choose at least one role")
            Set<@NotNull RoleName> roles) {
    }

    public record TemporaryPasswordIssue(
            @NotBlank(message = "Temporary password is required")
            @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH,
                    message = "Temporary password must be 10 to 128 characters")
            String temporaryPassword) {
    }

    /** One account in the list. */
    public record UserSummary(
            @Schema(example = "57") Long accountId,
            @Schema(example = "25CSE104") String loginId,
            @Schema(description = "The full name, or null when no person profile is recorded.") String fullName,
            @Schema(description = "The official email, or null.") String email,
            @Schema(description = "Every role, in the order STUDENT, FACULTY, ADMIN.") List<String> roles,
            @Schema(allowableValues = {"ACTIVE", "DISABLED"}) String status,
            @Schema(description = "Whether the person must choose a new password before anything else.")
            boolean passwordChangeRequired) {
    }

    public record UserPage(
            List<UserSummary> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "37") long totalElements,
            @Schema(example = "2") int totalPages) {
    }

    /** One account with its person details and academic profiles. Never a password, a hash, or a phone number. */
    public record UserDetail(
            Long accountId,
            String loginId,
            String fullName,
            String preferredName,
            String email,
            List<String> roles,
            String status,
            boolean passwordChangeRequired,
            @Schema(description = "When the current temporary password stops working, or null.")
            Instant temporaryPasswordExpiresAt,
            Instant createdAt,
            StudentProfileSummary studentProfile,
            FacultyProfileSummary facultyProfile) {
    }

    public record StudentProfileSummary(
            Long studentProfileId,
            String programCode,
            String programName,
            String admissionSessionCode,
            String rollNumber,
            String status) {
    }

    public record FacultyProfileSummary(
            Long facultyProfileId,
            String departmentCode,
            String departmentName,
            String status) {
    }
}
