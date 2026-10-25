package com.stacc.backend.support.helpdesk;

import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.account.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The helpdesk as clients see it. People appear only by login ID; no account ID, password hash, role, or permission is
 * ever included, and no entity is sent as it is.
 */
public final class HelpdeskResponses {

    private HelpdeskResponses() {
    }

    private static String loginIdOf(UserAccount account) {
        return account == null ? null : account.getLoginId();
    }

    /** A category that can be chosen for a new ticket. */
    public record HelpdeskCategoryResponse(
            @Schema(example = "3") Long categoryId,
            @Schema(example = "LMS") String code,
            @Schema(example = "LMS and courses") String name,
            @Schema(nullable = true) String description) {

        static HelpdeskCategoryResponse of(HelpdeskCategory category) {
            return new HelpdeskCategoryResponse(category.getId(), category.getCode(), category.getName(),
                    category.getDescription());
        }
    }

    /** A category as admins manage it, active or not. */
    public record HelpdeskCategoryAdminResponse(
            @Schema(example = "3") Long categoryId,
            @Schema(example = "LMS") String code,
            @Schema(example = "LMS and courses") String name,
            @Schema(nullable = true) String description,
            @Schema(example = "true") boolean active,
            Instant createdAt,
            Instant updatedAt) {

        static HelpdeskCategoryAdminResponse of(HelpdeskCategory category) {
            return new HelpdeskCategoryAdminResponse(category.getId(), category.getCode(), category.getName(),
                    category.getDescription(), category.isActive(), category.getCreatedAt(), category.getUpdatedAt());
        }
    }

    /** One ticket in a list: no description, messages, or history. */
    public record HelpdeskTicketSummary(
            @Schema(example = "42") Long ticketId,
            @Schema(example = "STC-000042") String ticketNumber,
            @Schema(example = "LMS") String categoryCode,
            @Schema(example = "LMS and courses") String categoryName,
            @Schema(example = "Cannot open my CS302 course") String subject,
            @Schema(allowableValues = {"LOW", "NORMAL", "HIGH", "URGENT"}) String priority,
            @Schema(allowableValues = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"}) String status,
            @Schema(example = "student1") String requesterLoginId,
            @Schema(nullable = true, example = "admin1") String assignedToLoginId,
            Instant createdAt,
            Instant updatedAt) {

        static HelpdeskTicketSummary of(HelpdeskTicket ticket) {
            return new HelpdeskTicketSummary(ticket.getId(), ticket.getTicketNumber(), ticket.getCategory().getCode(),
                    ticket.getCategory().getName(), ticket.getSubject(), ticket.getPriority().name(),
                    ticket.getStatus().name(), loginIdOf(ticket.getRequester()), loginIdOf(ticket.getAssignedTo()),
                    ticket.getCreatedAt(), ticket.getUpdatedAt());
        }
    }

    /** One page of tickets, newest first. */
    public record HelpdeskTicketPage(
            List<HelpdeskTicketSummary> items,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "37") long totalElements,
            @Schema(example = "2") int totalPages) {
    }

    /** One message of the conversation. */
    public record HelpdeskMessageResponse(
            @Schema(example = "7") Long messageId,
            @Schema(example = "admin1") String authorLoginId,
            @Schema(description = "Plain text.") String message,
            Instant createdAt) {

        static HelpdeskMessageResponse of(HelpdeskTicketMessage message) {
            return new HelpdeskMessageResponse(message.getId(), loginIdOf(message.getAuthor()), message.getMessage(),
                    message.getCreatedAt());
        }
    }

    /** One entry of the audit trail. */
    public record HelpdeskHistoryResponse(
            @Schema(allowableValues = {"CREATED", "MESSAGE_ADDED", "STATUS_CHANGED", "PRIORITY_CHANGED", "ASSIGNED",
                    "UNASSIGNED"}) String action,
            @Schema(nullable = true, example = "OPEN") String fromValue,
            @Schema(nullable = true, example = "IN_PROGRESS") String toValue,
            @Schema(example = "admin1") String actorLoginId,
            Instant createdAt) {

        static HelpdeskHistoryResponse of(HelpdeskTicketHistory entry) {
            return new HelpdeskHistoryResponse(entry.getAction().name(), entry.getFromValue(), entry.getToValue(),
                    loginIdOf(entry.getActor()), entry.getCreatedAt());
        }
    }

    /** One ticket in full: the original description, then the conversation and the history, oldest first. */
    public record HelpdeskTicketDetail(
            @Schema(example = "42") Long ticketId,
            @Schema(example = "STC-000042") String ticketNumber,
            @Schema(example = "LMS") String categoryCode,
            @Schema(example = "LMS and courses") String categoryName,
            @Schema(example = "true") boolean categoryActive,
            String subject,
            @Schema(description = "Plain text, the original description.") String description,
            @Schema(allowableValues = {"LOW", "NORMAL", "HIGH", "URGENT"}) String priority,
            @Schema(allowableValues = {"OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"}) String status,
            @Schema(example = "student1") String requesterLoginId,
            @Schema(nullable = true, example = "admin1") String assignedToLoginId,
            @Schema(nullable = true) Instant resolvedAt,
            @Schema(nullable = true) Instant closedAt,
            Instant createdAt,
            Instant updatedAt,
            List<HelpdeskMessageResponse> messages,
            List<HelpdeskHistoryResponse> history) {

        static HelpdeskTicketDetail of(HelpdeskTicket ticket, List<HelpdeskTicketMessage> messages,
                List<HelpdeskTicketHistory> history) {
            HelpdeskCategory category = ticket.getCategory();
            return new HelpdeskTicketDetail(ticket.getId(), ticket.getTicketNumber(), category.getCode(),
                    category.getName(), category.isActive(), ticket.getSubject(), ticket.getDescription(),
                    ticket.getPriority().name(), ticket.getStatus().name(), loginIdOf(ticket.getRequester()),
                    loginIdOf(ticket.getAssignedTo()), ticket.getResolvedAt(), ticket.getClosedAt(),
                    ticket.getCreatedAt(), ticket.getUpdatedAt(),
                    messages.stream().map(HelpdeskMessageResponse::of).toList(),
                    history.stream().map(HelpdeskHistoryResponse::of).toList());
        }
    }
}
