package com.stacc.backend.lms.ai.teacher;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiTeacherDocumentRepository extends JpaRepository<AiTeacherDocument, Long> {

    List<AiTeacherDocument> findByLmsCourseIdAndStatusOrderByIdAsc(Long lmsCourseId, AiTeacherDocument.Status status);

    long countByLmsCourseIdAndStatus(Long lmsCourseId, AiTeacherDocument.Status status);

    @Query("select document from AiTeacherDocument document join fetch document.lmsCourse where document.id = :id")
    Optional<AiTeacherDocument> findWithCourseById(@Param("id") Long id);
}
