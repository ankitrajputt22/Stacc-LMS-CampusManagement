package com.stacc.backend.notification;

import java.time.Instant;
import java.util.regex.Pattern;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One logical notification: what happened, in plain text, with a loose reference to the record it is about and an
 * optional path inside the application to look at it. It is written once, never changed, and never deleted. Who
 * receives it, and whether they have read it, is kept per recipient in {@link NotificationRecipient}.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_MESSAGE_LENGTH = 2000;
    public static final int MAX_ACTION_PATH_LENGTH = 300;

    // An application path: one leading slash, then path and query characters only. No scheme, host, backslash,
    // whitespace, or "//" that a browser could read as another site.
    private static final Pattern SAFE_PATH = Pattern.compile("/(?![/\\\\])[A-Za-z0-9/_.~%?=&-]*");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    // Plain text. It is never treated as HTML.
    @Column(nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 40)
    private NotificationSourceType sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "action_path", length = MAX_ACTION_PATH_LENGTH)
    private String actionPath;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // Required by JPA.
    }

    /**
     * @param sourceType what the notification is about, or null; given together with sourceId
     * @param actionPath a path inside the application, such as /calendar, or null
     */
    public Notification(NotificationType type, String title, String message, NotificationSourceType sourceType,
            Long sourceId, String actionPath) {
        this.type = Require.notNull(type, "type");
        this.title = Require.text(title, "title", MAX_TITLE_LENGTH);
        this.message = Require.text(message, "message", MAX_MESSAGE_LENGTH);
        if ((sourceType == null) != (sourceId == null)) {
            throw new IllegalArgumentException("sourceType and sourceId are given together or not at all");
        }
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        if (actionPath != null && !isSafeActionPath(actionPath)) {
            throw new IllegalArgumentException("actionPath must be a path inside the application");
        }
        this.actionPath = actionPath;
    }

    /** Whether the path stays inside the application: "/calendar" yes; "https://…", "//host", or "/\host" never. */
    public static boolean isSafeActionPath(String path) {
        return path != null && path.length() <= MAX_ACTION_PATH_LENGTH && !path.contains("..")
                && SAFE_PATH.matcher(path).matches();
    }

    public Long getId() {
        return id;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public NotificationSourceType getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getActionPath() {
        return actionPath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
