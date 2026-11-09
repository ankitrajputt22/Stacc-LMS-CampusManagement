package com.stacc.backend.hostel.outing;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One crossing of the gate, recorded by security. Append-only: the movement log is never edited or deleted. */
@Entity
@Table(name = "gate_movements")
public class GateMovement {

    public enum Direction { OUT, IN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_user_account_id", nullable = false, updatable = false)
    private Long studentAccountId;

    @Column(name = "outing_id", updatable = false)
    private Long outingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Direction direction;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by_user_account_id", nullable = false, updatable = false)
    private Long recordedBy;

    protected GateMovement() {
        // Required by JPA.
    }

    public GateMovement(Long studentAccountId, Long outingId, Direction direction, Instant recordedAt, Long recordedBy) {
        this.studentAccountId = studentAccountId;
        this.outingId = outingId;
        this.direction = direction;
        this.recordedAt = recordedAt;
        this.recordedBy = recordedBy;
    }

    public Long getId() { return id; }
    public Long getStudentAccountId() { return studentAccountId; }
    public Long getOutingId() { return outingId; }
    public Direction getDirection() { return direction; }
    public Instant getRecordedAt() { return recordedAt; }
    public Long getRecordedBy() { return recordedBy; }
}
