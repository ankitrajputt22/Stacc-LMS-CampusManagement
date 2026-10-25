package com.stacc.backend.identity.student;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    Optional<StudentProfile> findByUserAccountId(Long userAccountId);

    /**
     * The student profile of an account, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). Features
     * that allow one current record per student, such as a hostel application per session, take this lock first, so
     * two requests of the same student take turns.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select studentProfile from StudentProfile studentProfile where studentProfile.userAccount.id = :userAccountId")
    Optional<StudentProfile> findByUserAccountIdForUpdate(@Param("userAccountId") Long userAccountId);

    /** One student profile, locked until the transaction ends ({@code SELECT ... FOR UPDATE}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select studentProfile from StudentProfile studentProfile where studentProfile.id = :studentProfileId")
    Optional<StudentProfile> findByIdForUpdate(@Param("studentProfileId") Long studentProfileId);

    /**
     * Student profiles whose account login ID contains the given text, with the account, program, and admission
     * session, by login ID. The pattern uses {@code !} as its escape character, so callers can match a literal
     * {@code %}, {@code _}, or {@code !}.
     */
    @Query("""
            select studentProfile
            from StudentProfile studentProfile
            join fetch studentProfile.userAccount userAccount
            join fetch studentProfile.program
            join fetch studentProfile.admissionSession
            where userAccount.loginId like :pattern escape '!'
            order by userAccount.loginId asc, studentProfile.id asc
            """)
    List<StudentProfile> searchByLoginId(@Param("pattern") String pattern, Pageable page);

    @Query("""
            select studentProfile
            from StudentProfile studentProfile
            join fetch studentProfile.userAccount
            join fetch studentProfile.program
            join fetch studentProfile.admissionSession
            where studentProfile.id = :studentProfileId
            """)
    Optional<StudentProfile> findWithContextById(@Param("studentProfileId") Long studentProfileId);
}
