package com.stacc.backend.support.helpdesk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What clients may send to the helpdesk. No request names a requester, author, actor, ticket number, or timestamp:
 * those always come from the server and the access token.
 */
public final class HelpdeskRequests {

    private HelpdeskRequests() {
    }

    public record CreateHelpdeskTicketRequest(
            @Schema(description = "An active category. It is loaded on the server.", example = "3")
            @NotNull(message = "Category is required")
            Long categoryId,

            @Schema(description = "Plain text.", example = "Cannot open my CS302 course")
            @NotBlank(message = "Subject is required")
            @Size(max = HelpdeskTicket.MAX_SUBJECT_LENGTH, message = "Subject must be at most 200 characters")
            String subject,

            @Schema(description = "Plain text, the original description of the issue.",
                    example = "The course is listed on my enrollment but not under Courses.")
            @NotBlank(message = "Description is required")
            @Size(max = HelpdeskTicket.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 10000 characters")
            String description,

            @Schema(description = "LOW, NORMAL, or HIGH; NORMAL when left out. URGENT is set only by the support team.",
                    allowableValues = {"LOW", "NORMAL", "HIGH"}, nullable = true, example = "NORMAL")
            HelpdeskTicketPriority priority) {
    }

    public record AddHelpdeskMessageRequest(
            @Schema(description = "Plain text.", example = "I have attached nothing; the course code is CS302.")
            @NotBlank(message = "Message is required")
            @Size(max = HelpdeskTicketMessage.MAX_MESSAGE_LENGTH, message = "Message must be at most 10000 characters")
            String message) {
    }

    public record AssignHelpdeskTicketRequest(
            @Schema(description = "The login ID of an admin account, or null to unassign.", nullable = true,
                    example = "admin1")
            @Size(max = 100, message = "Login ID must be at most 100 characters")
            String assignedToLoginId) {
    }

    public record ChangeHelpdeskTicketPriorityRequest(
            @Schema(allowableValues = {"LOW", "NORMAL", "HIGH", "URGENT"}, example = "HIGH")
            @NotNull(message = "Priority is required")
            HelpdeskTicketPriority priority) {
    }

    public record ChangeHelpdeskTicketStatusRequest(
            @Schema(description = "OPEN, IN_PROGRESS, RESOLVED, or CLOSED. A ticket only moves forward.",
                    allowableValues = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"}, example = "IN_PROGRESS")
            @NotNull(message = "Status is required")
            HelpdeskTicketStatus status) {
    }

    public record CreateHelpdeskCategoryRequest(
            @Schema(description = "Stored in uppercase: letters, digits, and underscores. Unique, and never changed.",
                    example = "LMS")
            @NotBlank(message = "Code is required")
            @Size(max = HelpdeskCategory.MAX_CODE_LENGTH, message = "Code must be at most 50 characters")
            String code,

            @Schema(example = "LMS and courses")
            @NotBlank(message = "Name is required")
            @Size(max = HelpdeskCategory.MAX_NAME_LENGTH, message = "Name must be at most 120 characters")
            String name,

            @Schema(description = "Optional plain text.", nullable = true, example = "Course access, content, and assignments.")
            @Size(max = HelpdeskCategory.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 1000 characters")
            String description,

            @Schema(description = "Whether it can be chosen for new tickets; true when left out.", nullable = true,
                    example = "true")
            Boolean active) {
    }

    public record UpdateHelpdeskCategoryRequest(
            @Schema(example = "LMS and courses")
            @NotBlank(message = "Name is required")
            @Size(max = HelpdeskCategory.MAX_NAME_LENGTH, message = "Name must be at most 120 characters")
            String name,

            @Schema(nullable = true, example = "Course access, content, and assignments.")
            @Size(max = HelpdeskCategory.MAX_DESCRIPTION_LENGTH, message = "Description must be at most 1000 characters")
            String description,

            @Schema(description = "An inactive category stays on its tickets but cannot be chosen for new ones.",
                    example = "true")
            @NotNull(message = "Active is required")
            Boolean active) {
    }
}
