package com.stacc.backend.lms.ai.notes;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiStudentNoteIndexRepository extends JpaRepository<AiStudentNoteIndex, Long> {

    Optional<AiStudentNoteIndex> findByLmsCourseIdAndUserAccountId(Long lmsCourseId, Long userAccountId);

    @Transactional
    @Modifying
    @Query("delete from AiStudentNoteIndex row where row.lmsCourseId = :lmsCourseId and row.userAccountId = :userAccountId")
    int deleteForStudent(@Param("lmsCourseId") Long lmsCourseId, @Param("userAccountId") Long userAccountId);
}
