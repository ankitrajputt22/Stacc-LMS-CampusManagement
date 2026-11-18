package com.stacc.backend.identity.person;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "person_emergency_contacts")
public class PersonEmergencyContact {

    public static final int MAX_PER_PERSON = 3;
    public static final int MAX_RELATION = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_profile_id", nullable = false, updatable = false)
    private PersonProfile personProfile;

    @Column(nullable = false, length = PersonProfileValues.MAX_FULL_NAME)
    private String name;

    @Column(nullable = false, length = MAX_RELATION)
    private String relation;

    @Column(nullable = false, length = PersonProfileValues.MAX_PHONE)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PersonEmergencyContact() {
    }

    public PersonEmergencyContact(PersonProfile personProfile, String name, String relation, String phone, Instant createdAt) {
        this.personProfile = personProfile;
        this.name = PersonProfileValues.contactName(name);
        this.relation = PersonProfileValues.relation(relation);
        String validPhone = PersonProfileValues.phone(phone);
        if (validPhone == null) {
            throw new IllegalArgumentException("Phone number is required.");
        }
        this.phone = validPhone;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public PersonProfile getPersonProfile() {
        return personProfile;
    }

    public String getName() {
        return name;
    }

    public String getRelation() {
        return relation;
    }

    public String getPhone() {
        return phone;
    }
}
