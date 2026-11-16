package com.stacc.backend.identity.person;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonEmergencyContactRepository extends JpaRepository<PersonEmergencyContact, Long> {

    List<PersonEmergencyContact> findAllByPersonProfileIdOrderByIdAsc(Long personProfileId);
}
