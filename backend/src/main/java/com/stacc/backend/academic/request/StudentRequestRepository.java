package com.stacc.backend.academic.request;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRequestRepository extends JpaRepository<StudentRequest, Long> {

    @Query("select r from StudentRequest r where r.requester.id = :accountId order by r.createdAt desc, r.id desc")
    List<StudentRequest> findAllByRequester(@Param("accountId") Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StudentRequest r join fetch r.requester where r.id = :id")
    Optional<StudentRequest> findByIdForUpdate(@Param("id") Long id);

    /** The queue: filtered by kind, status, and the requester's login ID (lower-cased, escaped LIKE), newest first. */
    @Query("""
            select r from StudentRequest r join fetch r.requester requester
            where (:kind is null or r.kind = :kind)
              and (:status is null or r.status = :status)
              and (:loginLike is null or lower(requester.loginId) like :loginLike escape '!')
            order by case when r.status = com.stacc.backend.academic.request.StudentRequest.Status.SUBMITTED then 0 else 1 end,
                     r.createdAt desc, r.id desc
            """)
    List<StudentRequest> queue(@Param("kind") StudentRequest.Kind kind, @Param("status") StudentRequest.Status status,
            @Param("loginLike") String loginLike, Pageable page);
}
