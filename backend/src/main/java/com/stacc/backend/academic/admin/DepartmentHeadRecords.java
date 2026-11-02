package com.stacc.backend.academic.admin;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** What admins send and receive when managing who heads each department. Never contact details. */
public final class DepartmentHeadRecords {

    private DepartmentHeadRecords() {
    }

    public record DepartmentHeadRow(
            @Schema(example = "1") Long departmentId,
            @Schema(example = "CSE") String departmentCode,
            @Schema(example = "Computer Science and Engineering") String departmentName,
            @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String departmentStatus,
            @Schema(description = "The active term, or null when the department has no head.") HeadTerm head) {
    }

    public record HeadTerm(
            @Schema(example = "4") Long departmentHeadId,
            @Schema(example = "12") Long userAccountId,
            @Schema(example = "hod1") String loginId,
            @Schema(description = "The person's name, or null before the college records one.") String name,
            @Schema(allowableValues = {"ACTIVE", "ENDED"}) String status,
            Instant startedAt,
            @Schema(description = "Null while the term is active.") Instant endedAt,
            @Schema(description = "Whether an active term counts now: the account is active and still holds HOD.")
            boolean counts,
            @Schema(description = "Why an active term does not count, or null.") String notCountedReason) {
    }

    public record HeadCandidate(
            @Schema(example = "12") Long userAccountId,
            @Schema(example = "hod1") String loginId,
            @Schema(description = "Or null before the college records a name.") String name) {
    }

    public record AssignDepartmentHead(
            @Schema(description = "An active account holding HOD. It is loaded on the server.", example = "12")
            @NotNull(message = "Account is required")
            Long userAccountId) {
    }
}
