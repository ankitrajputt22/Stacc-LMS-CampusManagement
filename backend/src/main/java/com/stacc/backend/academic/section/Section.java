package com.stacc.backend.academic.section;

import java.time.Instant;
import java.util.Locale;

import com.stacc.backend.academic.semester.Semester;
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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "sections", uniqueConstraints = @UniqueConstraint(name = "uk_sections_semester_code",
        columnNames = {"semester_id", "code"}))
public class Section {

    public static final int MAX_CODE_LENGTH = 10;
    public static final int MAX_NAME_LENGTH = 60;
    public static final int MAX_CAPACITY = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false, updatable = false)
    private Semester semester;

    @Column(nullable = false, length = MAX_CODE_LENGTH, updatable = false)
    private String code;

    @Column(length = MAX_NAME_LENGTH)
    private String name;

    @Column
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SectionStatus status = SectionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Section() {
    }

    public Section(Semester semester, String code, String name, Integer capacity) {
        this.semester = Require.notNull(semester, "semester");
        this.code = Require.text(code, "code", MAX_CODE_LENGTH).toUpperCase(Locale.ROOT);
        rename(name);
        changeCapacity(capacity);
    }

    public Long getId() {
        return id;
    }

    public Semester getSemester() {
        return semester;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public SectionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void rename(String name) {
        this.name = Require.optionalText(name, "name", MAX_NAME_LENGTH);
    }

    public void changeCapacity(Integer capacity) {
        if (capacity != null) {
            Require.range(capacity, "capacity", 1, MAX_CAPACITY);
        }
        this.capacity = capacity;
    }

    public void activate() {
        this.status = SectionStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = SectionStatus.INACTIVE;
    }
}
