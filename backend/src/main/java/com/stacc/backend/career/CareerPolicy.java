package com.stacc.backend.career;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The college's placement policy: one row. With oneAcceptedPlacement, a student who accepted a placement applies to no more. */
@Entity
@Table(name = "career_policy")
public class CareerPolicy {

    @Id
    private Long id;

    @Column(name = "one_accepted_placement", nullable = false)
    private boolean oneAcceptedPlacement;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CareerPolicy() {
        // Required by JPA.
    }

    public void change(boolean oneAcceptedPlacement, Instant at) {
        this.oneAcceptedPlacement = oneAcceptedPlacement;
        this.updatedAt = at;
    }

    public boolean isOneAcceptedPlacement() { return oneAcceptedPlacement; }
}
