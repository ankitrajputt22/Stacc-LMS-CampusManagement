package com.stacc.backend.hackathon;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HackathonRepository extends JpaRepository<Hackathon, Long> {

    @Query("""
            select h from Hackathon h where h.status in (com.stacc.backend.hackathon.Hackathon.Status.PUBLISHED,
            com.stacc.backend.hackathon.Hackathon.Status.RESULTS_RELEASED) order by h.startsAt desc, h.id desc
            """)
    List<Hackathon> findVisible();

    @Query("select h from Hackathon h order by h.startsAt desc, h.id desc")
    List<Hackathon> findAllForOffice();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hackathon h where h.id = :id")
    Optional<Hackathon> findByIdForUpdate(@Param("id") Long id);
}
