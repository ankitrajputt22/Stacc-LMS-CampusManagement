package com.stacc.backend.campus.services;

import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One campus service in the catalog, such as an ID card replacement. It is configurable data, never an enum: its code
 * is set once, it says which roles may request it, and whether requests need approval through an approval workflow,
 * named by its stable code. An inactive service takes no new requests; it is never deleted.
 */
@Entity
@Table(name = "campus_service_definitions")
public class CampusServiceDefinition {

    public static final int MAX_CODE_LENGTH = 80;
    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 3000;
    static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]*");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = MAX_CODE_LENGTH)
    private String code;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "available_to_student", nullable = false)
    private boolean availableToStudent;

    @Column(name = "available_to_faculty", nullable = false)
    private boolean availableToFaculty;

    @Column(name = "approval_required", nullable = false)
    private boolean approvalRequired;

    // The workflow's stable code, not a relation: the catalog only names the workflow it starts.
    @Column(name = "approval_workflow_code", length = 80)
    private String approvalWorkflowCode;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CampusServiceDefinition() {
        // Required by JPA.
    }

    public CampusServiceDefinition(String code, Settings settings) {
        this.code = normalizedCode(code);
        update(settings);
    }

    /** Everything about a service but its code. */
    public record Settings(String name, String description, boolean active, boolean availableToStudent,
            boolean availableToFaculty, boolean approvalRequired, String approvalWorkflowCode) {
    }

    public static String normalizedCode(String code) {
        String normalized = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be letters, digits, and underscores, starting with a letter");
        }
        return normalized;
    }

    /**
     * Replaces everything but the code. A service is offered to at least one role; one that needs approval names its
     * workflow, and one that does not names none. Whether the workflow exists is checked by the caller.
     */
    public void update(Settings settings) {
        Require.notNull(settings, "settings");
        if (!settings.availableToStudent() && !settings.availableToFaculty()) {
            throw new IllegalArgumentException("a service is available to students, faculty, or both");
        }
        this.name = Require.text(settings.name(), "name", MAX_NAME_LENGTH);
        this.description = Require.optionalText(settings.description(), "description", MAX_DESCRIPTION_LENGTH);
        this.active = settings.active();
        this.availableToStudent = settings.availableToStudent();
        this.availableToFaculty = settings.availableToFaculty();
        this.approvalRequired = settings.approvalRequired();
        this.approvalWorkflowCode = settings.approvalRequired()
                ? Require.text(settings.approvalWorkflowCode(), "approvalWorkflowCode", 80).toUpperCase(Locale.ROOT)
                : null;
    }

    /** Whether an account holding these role codes may request the service. */
    public boolean isAvailableTo(Collection<String> roleCodes) {
        return (availableToStudent && roleCodes.contains("STUDENT")) || (availableToFaculty && roleCodes.contains("FACULTY"));
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isAvailableToStudent() {
        return availableToStudent;
    }

    public boolean isAvailableToFaculty() {
        return availableToFaculty;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public String getApprovalWorkflowCode() {
        return approvalWorkflowCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
