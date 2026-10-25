package com.stacc.backend.approval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.stacc.backend.auth.role.Role;
import com.stacc.backend.common.validation.Require;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A reusable approval workflow: a stable code, a name, and ordered steps, each decided by one role. It is configuration
 * only. Requests copy its steps when they start, so editing a workflow changes future requests only. A workflow is made
 * inactive, never deleted; an inactive one starts no new requests.
 */
@Entity
@Table(name = "approval_workflow_definitions")
public class ApprovalWorkflowDefinition {

    public static final int MAX_CODE_LENGTH = 80;
    public static final int MAX_NAME_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_STEPS = 10;
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

    // The steps belong to the workflow and are replaced with it. Requests never point at them: they keep snapshots.
    @OneToMany(mappedBy = "workflowDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepOrder ASC")
    private List<ApprovalWorkflowStepDefinition> steps = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApprovalWorkflowDefinition() {
        // Required by JPA.
    }

    /** A new active workflow. The code is stored in uppercase and never changes. */
    public ApprovalWorkflowDefinition(String code, String name, String description, List<StepSpec> steps) {
        this.code = normalizedCode(code);
        update(name, description, true, steps);
    }

    /** The code as it is stored: trimmed and uppercase. Anything that is not a valid code is refused. */
    public static String normalizedCode(String code) {
        String normalized = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("code must be letters, digits, and underscores, starting with a letter");
        }
        return normalized;
    }

    /**
     * Replaces the name, description, active flag, and steps. Steps are numbered from 1 in the order given. Existing
     * rows are changed in place, extra ones added, and surplus ones removed, so a step number is never used twice.
     */
    public void update(String name, String description, boolean active, List<StepSpec> newSteps) {
        this.name = Require.text(name, "name", MAX_NAME_LENGTH);
        this.description = Require.optionalText(description, "description", MAX_DESCRIPTION_LENGTH);
        this.active = active;
        Require.notNull(newSteps, "steps");
        if (newSteps.isEmpty() || newSteps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("a workflow has 1 to " + MAX_STEPS + " steps");
        }
        for (int index = 0; index < newSteps.size(); index++) {
            StepSpec spec = Require.notNull(newSteps.get(index), "step");
            if (index < steps.size()) {
                steps.get(index).change(spec.name(), spec.approverRole());
            } else {
                steps.add(new ApprovalWorkflowStepDefinition(this, index + 1, spec.name(), spec.approverRole()));
            }
        }
        while (steps.size() > newSteps.size()) {
            steps.remove(steps.size() - 1);
        }
    }

    /** One step as an admin describes it: a name and the role that decides it. Its number is its place in the list. */
    public record StepSpec(String name, Role approverRole) {
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

    public List<ApprovalWorkflowStepDefinition> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
