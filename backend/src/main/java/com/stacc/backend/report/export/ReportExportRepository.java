package com.stacc.backend.report.export;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportExportRepository extends JpaRepository<ReportExport, Long> {

    @Query("select e from ReportExport e where e.accountId = :accountId order by e.createdAt desc")
    List<ReportExport> findRecent(@Param("accountId") Long accountId, Pageable page);

    @Query("select e from ReportExport e where e.status = com.stacc.backend.report.export.ReportExport.Status.READY and e.expiresAt < :now")
    List<ReportExport> findExpired(@Param("now") Instant now, Pageable page);
}
