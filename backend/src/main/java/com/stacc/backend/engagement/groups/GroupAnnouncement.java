package com.stacc.backend.engagement.groups;

import java.time.Instant;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A plain-text announcement of a club or sports group to its members. Kept as written; never changed or deleted. */
@Entity
@Table(name = "engagement_group_announcements")
public class GroupAnnouncement {

    public static final int MAX_MESSAGE_LENGTH = 5_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    private CampusGroup group;

    @Column(nullable = false, updatable = false, length = 200)
    private String title;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private Long createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GroupAnnouncement() {
        // Required by JPA.
    }

    public GroupAnnouncement(CampusGroup group, String title, String message, Long by, Instant now) {
        this.group = Require.notNull(group, "group");
        this.title = Require.text(title, "Title", 200);
        this.message = Require.text(message, "Message", MAX_MESSAGE_LENGTH);
        this.createdByAccountId = Require.notNull(by, "createdBy");
        this.createdAt = Require.notNull(now, "now");
    }

    public Long getId() { return id; }
    public CampusGroup getGroup() { return group; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
