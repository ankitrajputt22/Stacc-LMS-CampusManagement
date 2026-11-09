package com.stacc.backend.documents;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VaultDocumentRepository extends JpaRepository<VaultDocument, Long> {

    @Query("select d from VaultDocument d where d.owner.id = :accountId order by d.createdAt desc, d.id desc")
    List<VaultDocument> findAllForOwner(@Param("accountId") Long accountId);

    Optional<VaultDocument> findByVerificationFingerprint(String verificationFingerprint);
}
