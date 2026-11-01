package com.stacc.backend.academic.admin;

import com.stacc.backend.academic.teaching.TeachingAssignmentStatus;
import com.stacc.backend.academic.teaching.TeachingRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** What an admin sends to assign faculty to a course offering or change an assignment. Never an account ID. */
public final class FacultyAssignmentRequests {

    private FacultyAssignmentRequests() {
    }

    public record FacultyAssignmentCreate(
            @Schema(description = "The course offering to teach. It is loaded on the server.", example = "31")
            @NotNull(message = "Course offering is required")
            Long courseOfferingId,

            @Schema(description = "An active faculty profile. It is loaded on the server.", example = "11")
            @NotNull(message = "Faculty member is required")
            Long facultyProfileId,

            @Schema(description = "PRIMARY or CO_FACULTY. An offering has at most one active primary faculty member.",
                    allowableValues = {"PRIMARY", "CO_FACULTY"}, example = "PRIMARY")
            @NotNull(message = "Role is required")
            TeachingRole role) {
    }

    public record FacultyAssignmentUpdate(
            @Schema(allowableValues = {"PRIMARY", "CO_FACULTY"}, example = "CO_FACULTY")
            @NotNull(message = "Role is required")
            TeachingRole role,

            @Schema(description = "ACTIVE or INACTIVE. An inactive assignment is kept as history and gives no access.",
                    allowableValues = {"ACTIVE", "INACTIVE"}, example = "INACTIVE")
            @NotNull(message = "Status is required")
            TeachingAssignmentStatus status) {
    }
}
