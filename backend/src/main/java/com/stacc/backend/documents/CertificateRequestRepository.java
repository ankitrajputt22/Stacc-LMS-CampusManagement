package com.stacc.backend.documents;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CertificateRequestRepository extends JpaRepository<CertificateRequest, Long> {

    @Query("select r from CertificateRequest r where r.requester.id = :accountId order by r.createdAt desc")
    List<CertificateRequest> findAllForRequester(@Param("accountId") Long accountId);

    @Query("select r from CertificateRequest r join fetch r.requester where (:status is null or r.status = :status) order by r.createdAt asc")
    List<CertificateRequest> findAllByStatus(@Param("status") CertificateRequest.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CertificateRequest r where r.id = :id")
    Optional<CertificateRequest> findByIdForUpdate(@Param("id") Long id);
}
