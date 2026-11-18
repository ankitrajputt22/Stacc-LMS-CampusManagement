package com.stacc.backend.academic.exam;

import java.time.Instant;

import com.stacc.backend.academic.semester.Semester;
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

@Entity
@Table(name = "exam_sessions")
public class ExamSession {

    public enum Status { DRAFT, PUBLISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false, updatable = false)
    private Semester semester;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExamSession() {
    }

    public ExamSession(Semester semester, String name, Instant createdAt) {
        this.semester = semester;
        this.name = name;
        this.createdAt = createdAt;
    }

    public boolean publish(Instant at) {
        if (status == Status.PUBLISHED) {
            return false;
        }
        status = Status.PUBLISHED;
        publishedAt = at;
        return true;
    }

    public Long getId() {
        return id;
    }

    public Semester getSemester() {
        return semester;
    }

    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
