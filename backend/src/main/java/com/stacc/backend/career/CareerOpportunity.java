package com.stacc.backend.career;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * One opportunity: a placement, internship, research opening, or scholarship, with its eligible programs (empty means
 * every program), deadline, and ordered stages. Drafts are edited; open ones take applications until the deadline;
 * closed ones take none. Stages are fixed once it opens.
 */
@Entity
@Table(name = "career_opportunities")
public class CareerOpportunity {

    public enum Kind { PLACEMENT, INTERNSHIP, RESEARCH, SCHOLARSHIP }

    public enum Status { DRAFT, OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private CareerCompany company;

    @Column(length = 200)
    private String organization;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "eligible_program_ids", length = 500)
    private String eligibleProgramIds;

    @Column(nullable = false)
    private Instant deadline;

    @Column(nullable = false, length = 1000)
    private String stages;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "created_by_user_account_id", nullable = false, updatable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerOpportunity() {
        // Required by JPA.
    }

    public CareerOpportunity(Long createdBy) {
        this.createdBy = createdBy;
    }

    public void edit(Kind kind, String title, CareerCompany company, String organization, String description, List<Long> programIds,
            Instant deadline, List<String> stageNames) {
        if (status != Status.DRAFT) {
            throw new IllegalStateException("Only a draft can be edited");
        }
        if (stageNames == null || stageNames.isEmpty() || stageNames.size() > 10
                || stageNames.stream().anyMatch(name -> name == null || name.isBlank() || name.length() > 60 || name.contains("|"))) {
            throw new IllegalArgumentException("give 1 to 10 stage names of at most 60 characters");
        }
        this.kind = Require.notNull(kind, "kind");
        this.title = Require.text(title, "title", 200);
        this.company = company;
        this.organization = Require.optionalText(organization, "organization", 200);
        this.description = Require.optionalText(description, "description", 10_000);
        this.eligibleProgramIds = programIds == null || programIds.isEmpty() ? null
                : programIds.stream().distinct().map(String::valueOf).collect(Collectors.joining(","));
        this.deadline = Require.notNull(deadline, "deadline");
        this.stages = stageNames.stream().map(String::strip).collect(Collectors.joining("|"));
    }

    public void open() {
        if (status == Status.DRAFT) {
            status = Status.OPEN;
        }
    }

    public void close() {
        status = Status.CLOSED;
    }

    public List<String> stageNames() {
        return Arrays.asList(stages.split("\\|"));
    }

    public List<Long> programIds() {
        return eligibleProgramIds == null ? List.of() : Arrays.stream(eligibleProgramIds.split(",")).map(Long::valueOf).toList();
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public CareerCompany getCompany() { return company; }
    public String getOrganization() { return organization; }
    public String getDescription() { return description; }
    public Instant getDeadline() { return deadline; }
    public Status getStatus() { return status; }
}
