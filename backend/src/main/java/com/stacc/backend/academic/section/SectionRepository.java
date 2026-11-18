package com.stacc.backend.academic.section;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SectionRepository extends JpaRepository<Section, Long> {

    String WITH_CONTEXT = """
            select section
            from Section section
            join fetch section.semester semester
            join fetch semester.program program
            join fetch semester.academicSession academicSession
            """;
    String ORDER = " order by academicSession.startDate desc, program.code, semester.semesterNumber, section.code";

    @Query(WITH_CONTEXT + ORDER)
    List<Section> findAllWithContext();

    @Query(WITH_CONTEXT + " where semester.id = :semesterId" + ORDER)
    List<Section> findAllWithContextBySemesterId(@Param("semesterId") Long semesterId);

    @Query(WITH_CONTEXT + " where section.id = :sectionId")
    Optional<Section> findWithContextById(@Param("sectionId") Long sectionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select section from Section section where section.id = :sectionId")
    Optional<Section> findByIdForUpdate(@Param("sectionId") Long sectionId);

    boolean existsBySemesterIdAndCode(Long semesterId, String code);
}
