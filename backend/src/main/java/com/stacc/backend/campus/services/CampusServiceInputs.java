package com.stacc.backend.campus.services;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What clients may send about campus services. No input names a requester, account, status, request number, or
 * approval request: those come from the server and the access token. A service's code is not part of its update.
 */
public final class CampusServiceInputs {

    private CampusServiceInputs() {
    }

    public record CreateCampusServiceInput(
            @Schema(description = "Stored in uppercase: letters, digits, and underscores, starting with a letter. Unique, and never changed.",
                    example = "ID_CARD")
            @NotBlank(message = "Code is required")
            @Size(max = CampusServiceDefinition.MAX_CODE_LENGTH, message = "Code must be at most 80 characters")
            String code,

            @Schema(example = "ID card replacement")
            @NotBlank(message = "Name is required")
            @Size(max = CampusServiceDefinition.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(description = "Optional plain text.", nullable = true)
            @Size(max = CampusServiceDefinition.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 3000 characters")
            String description,

            @Schema(description = "Whether the service takes new requests. True when left out.", nullable = true)
            Boolean active,

            @NotNull(message = "Available to students is required")
            Boolean availableToStudent,

            @NotNull(message = "Available to faculty is required")
            Boolean availableToFaculty,

            @NotNull(message = "Approval required is required")
            Boolean approvalRequired,

            @Schema(description = "The code of an existing, active approval workflow when approval is required; ignored otherwise.",
                    nullable = true, example = "CAMPUS_SERVICE_REQUEST")
            @Size(max = 80, message = "Approval workflow code must be at most 80 characters")
            String approvalWorkflowCode) {
    }

    public record UpdateCampusServiceInput(
            @NotBlank(message = "Name is required")
            @Size(max = CampusServiceDefinition.MAX_NAME_LENGTH, message = "Name must be at most 150 characters")
            String name,

            @Schema(nullable = true)
            @Size(max = CampusServiceDefinition.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 3000 characters")
            String description,

            @Schema(description = "An inactive service takes no new requests; its requests stay.")
            @NotNull(message = "Active is required")
            Boolean active,

            @NotNull(message = "Available to students is required")
            Boolean availableToStudent,

            @NotNull(message = "Available to faculty is required")
            Boolean availableToFaculty,

            @Schema(description = "Changing the approval settings affects future requests only.")
            @NotNull(message = "Approval required is required")
            Boolean approvalRequired,

            @Schema(nullable = true, example = "CAMPUS_SERVICE_REQUEST")
            @Size(max = 80, message = "Approval workflow code must be at most 80 characters")
            String approvalWorkflowCode) {
    }

    public record CreateCampusServiceRequestInput(
            @NotNull(message = "Service is required")
            Long serviceId,

            @Schema(description = "Plain text.", example = "Replacement ID card")
            @NotBlank(message = "Subject is required")
            @Size(max = CampusServiceRequest.MAX_SUBJECT_LENGTH, message = "Subject must be at most 200 characters")
            String subject,

            @Schema(description = "Plain text.")
            @NotBlank(message = "Details are required")
            @Size(max = CampusServiceRequest.MAX_DETAILS_LENGTH, message = "Details must be at most 10000 characters")
            String details) {
    }
}
