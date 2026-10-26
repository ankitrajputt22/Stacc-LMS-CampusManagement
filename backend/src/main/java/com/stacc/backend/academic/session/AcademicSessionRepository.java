package com.stacc.backend.academic.session;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicSessionRepository extends JpaRepository<AcademicSession, Long> {

    Optional<AcademicSession> findByCode(String code);

    /** Every academic session, the latest start first. */
    List<AcademicSession> findAllByOrderByStartDateDescIdDesc();

    // Compares as the unique constraint does, in the column's collation.
    boolean existsByCode(String code);

    /** Search: academic sessions, whatever their status, whose code contains the text; the latest start first. */
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
