package com.stacc.backend.audit;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The admin view of the audit log: newest first, filtered and paged on the server. It only reads. */
@Service
@Transactional(readOnly = true)
public class AuditQueryService {

    static final String BAD_PAGE = "Page must be 0 or more and size from 1 to 100.";
    static final String BAD_RANGE = "The end of the range must be after its start.";
    static final String QUERY_TOO_LONG = "Search text must be at most 100 characters.";

    private final AuditEventRepository events;
    private final UserAccountRepository accounts;

    public AuditQueryService(AuditEventRepository events, UserAccountRepository accounts) {
        this.events = events;
        this.accounts = accounts;
    }

    public record AuditRow(
            Long auditEventId,
            Instant occurredAt,
            @Schema(description = "The login ID of the account that acted, or null for the system.") String actorLoginId,
            String action,
            String targetType,
            Long targetId,
            @Schema(description = "The login ID when the target is an account, otherwise null.") String targetLoginId,
            @Schema(description = "Short plain text.") String details,
            @Schema(description = "The request ID, to find the matching log lines.") String requestId) {
    }

    public record AuditPage(List<AuditRow> items, int page, int size, long totalElements, int totalPages) {
    }

    public AuditPage search(AuditAction action, Instant from, Instant to, String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_PAGE);
        }
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, BAD_RANGE);
        }
        String text = query == null ? "" : query.strip();
        if (text.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, QUERY_TOO_LONG);
        }
        String pattern = text.isEmpty() ? null
                : "%" + text.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        Page<AuditEvent> found = events.search(action, from, to, pattern, PageRequest.of(page, size));

        Set<Long> accountIds = new HashSet<>();
        for (AuditEvent event : found.getContent()) {
            if (event.getActorAccountId() != null) {
                accountIds.add(event.getActorAccountId());
            }
            if (AuditService.USER_ACCOUNT.equals(event.getTargetType()) && event.getTargetId() != null) {
                accountIds.add(event.getTargetId());
            }
        }
        Map<Long, String> loginIds = accountIds.isEmpty() ? Map.of() : accounts.findAllById(accountIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getLoginId));
        List<AuditRow> rows = found.getContent().stream().map(event -> new AuditRow(event.getId(), event.getOccurredAt(),
                event.getActorAccountId() == null ? null : loginIds.get(event.getActorAccountId()),
                event.getAction().name(), event.getTargetType(), event.getTargetId(),
                AuditService.USER_ACCOUNT.equals(event.getTargetType()) ? loginIds.get(event.getTargetId()) : null,
                event.getDetails(), event.getRequestId())).toList();
        return new AuditPage(rows, found.getNumber(), found.getSize(), found.getTotalElements(), found.getTotalPages());
    }
}
