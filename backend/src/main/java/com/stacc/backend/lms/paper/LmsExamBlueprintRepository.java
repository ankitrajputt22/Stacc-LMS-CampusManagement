package com.stacc.backend.lms.paper;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsExamBlueprintRepository extends JpaRepository<LmsExamBlueprint, Long> {

    List<LmsExamBlueprint> findAllByLmsCourseIdOrderByIdDesc(Long lmsCourseId);

    @Query("select blueprint.lmsCourse.id from LmsExamBlueprint blueprint where blueprint.id = :blueprintId")
    Optional<Long> findLmsCourseIdById(@Param("blueprintId") Long blueprintId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select blueprint from LmsExamBlueprint blueprint join fetch blueprint.lmsCourse where blueprint.id = :blueprintId")
    Optional<LmsExamBlueprint> findByIdForUpdate(@Param("blueprintId") Long blueprintId);
}
