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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select studentProfile from StudentProfile studentProfile where studentProfile.userAccount.id = :userAccountId")
    Optional<StudentProfile> findByUserAccountIdForUpdate(@Param("userAccountId") Long userAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select studentProfile from StudentProfile studentProfile where studentProfile.id = :studentProfileId")
    Optional<StudentProfile> findByIdForUpdate(@Param("studentProfileId") Long studentProfileId);

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

    @Query("""
            select profile
            from StudentProfile profile
            join fetch profile.userAccount account
            join fetch profile.program program
            join fetch program.department
            join fetch profile.admissionSession
            where lower(account.loginId) like :contains escape '!'
               or lower(coalesce(profile.rollNumber, '')) like :contains escape '!'
            order by account.loginId asc
            """)
    List<StudentProfile> searchByLoginIdOrRollNumber(@Param("contains") String contains, Pageable page);

    @Query("""
            select profile
            from StudentProfile profile
            join fetch profile.userAccount
            join fetch profile.program program
            join fetch program.department
            join fetch profile.admissionSession
            where profile.userAccount.id = :userAccountId
            """)
    Optional<StudentProfile> findWithContextByUserAccountId(@Param("userAccountId") Long userAccountId);

    boolean existsByRollNumberAndIdNot(String rollNumber, Long id);

    boolean existsByRollNumber(String rollNumber);
}
