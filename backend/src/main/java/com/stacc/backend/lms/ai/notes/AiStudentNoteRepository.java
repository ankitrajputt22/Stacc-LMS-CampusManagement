package com.stacc.backend.lms.ai.notes;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiStudentNoteRepository extends JpaRepository<AiStudentNote, Long> {

    List<AiStudentNote> findByLmsCourseIdAndUserAccountIdOrderByIdAsc(Long lmsCourseId, Long userAccountId);

    long countByLmsCourseIdAndUserAccountId(Long lmsCourseId, Long userAccountId);

    Optional<AiStudentNote> findByIdAndLmsCourseIdAndUserAccountId(Long id, Long lmsCourseId, Long userAccountId);
}
