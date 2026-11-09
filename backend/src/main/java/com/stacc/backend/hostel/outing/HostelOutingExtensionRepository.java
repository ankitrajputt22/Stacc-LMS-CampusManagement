package com.stacc.backend.hostel.outing;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HostelOutingExtensionRepository extends JpaRepository<HostelOutingExtension, Long> {

    @Query("select e from HostelOutingExtension e where e.outing.id in :outingIds order by e.createdAt asc, e.id asc")
    List<HostelOutingExtension> findAllForOutings(@Param("outingIds") Collection<Long> outingIds);
}
