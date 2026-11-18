package com.stacc.backend.academic.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {

    Optional<AcademicSession> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from AcademicSession s where s.id = :id")
    Optional<AcademicSession> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AcademicSession> findFirstByCurrentTrue();

    Optional<AcademicSession> findByCurrentTrue();

    List<AcademicSession> findAllByOrderByStartDateDescIdDesc();

    boolean existsByCode(String code);

    @Query(value = """
            select academicSession
            from AcademicSession academicSession
            where lower(academicSession.code) like :contains escape '!'
            order by case when lower(academicSession.code) = :exact then 0 when lower(academicSession.code) like :prefix escape '!' then 1 else 2 end, academicSession.startDate desc, academicSession.id desc
            """,
            countQuery = """
            select count(academicSession)
            from AcademicSession academicSession
            where lower(academicSession.code) like :contains escape '!'
            """)
    Page<AcademicSession> search(@Param("exact") String exact, @Param("prefix") String prefix,
            @Param("contains") String contains, Pageable page);
}
