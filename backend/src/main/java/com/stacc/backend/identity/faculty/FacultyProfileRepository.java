package com.stacc.backend.identity.faculty;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacultyProfileRepository extends JpaRepository<FacultyProfile, Long> {

    Optional<FacultyProfile> findByUserAccountId(Long userAccountId);

    /**
     * Faculty profiles whose login ID contains the text (lower-cased and escaped by the caller), with the account and
     * department loaded, by login ID. For choosing who teaches an offering; it never searches names or contact details.
     */
    @Query("""
            select profile
            from FacultyProfile profile
            join fetch profile.userAccount account
            join fetch profile.department
            where lower(account.loginId) like :contains escape '!'
            order by account.loginId asc
            """)
    List<FacultyProfile> searchByLoginId(@Param("contains") String contains, Pageable page);

    @Query("""
            select profile
            from FacultyProfile profile
            join fetch profile.userAccount account
            join fetch account.roles
            join fetch profile.department
            where profile.id = :facultyProfileId
            """)
    Optional<FacultyProfile> findWithAccountById(@Param("facultyProfileId") Long facultyProfileId);
}
