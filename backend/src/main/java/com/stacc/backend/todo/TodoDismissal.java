package com.stacc.backend.todo;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One To-Do item an account has dismissed, by its key. */
@Entity
@Table(name = "todo_dismissals")
public class TodoDismissal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_account_id", nullable = false, updatable = false)
    private Long userAccountId;

    @Column(name = "item_key", nullable = false, updatable = false, length = 100)
    private String itemKey;

    @Column(name = "dismissed_at", nullable = false, updatable = false)
    private Instant dismissedAt;

    protected TodoDismissal() {
        // Required by JPA.
    }

    public TodoDismissal(Long userAccountId, String itemKey, Instant dismissedAt) {
        this.userAccountId = userAccountId;
        this.itemKey = itemKey;
        this.dismissedAt = dismissedAt;
    }

    public String getItemKey() {
        return itemKey;
    }
}
