package com.stacc.backend.lms.assignment.rubric;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsRubricRepository extends JpaRepository<LmsRubric, Long> {

    @Query("""
            select distinct rubric from LmsRubric rubric left join fetch rubric.criteria
            where rubric.lmsCourse.id = :lmsCourseId order by rubric.id desc
            """)
    List<LmsRubric> findAllForCourse(@Param("lmsCourseId") Long lmsCourseId);

    @Query("select rubric.lmsCourse.id from LmsRubric rubric where rubric.id = :rubricId")
    Optional<Long> findLmsCourseIdById(@Param("rubricId") Long rubricId);

    @Query(value = "select id from lms_rubrics where id = :rubricId for update", nativeQuery = true)
    List<Long> lockRowForUpdate(@Param("rubricId") Long rubricId);

    @Query(value = "select id from lms_rubrics where id = :rubricId for share", nativeQuery = true)
    List<Long> lockRowForShare(@Param("rubricId") Long rubricId);

    @Query(value = "select id from lms_rubric_criteria where lms_rubric_id = :rubricId for share", nativeQuery = true)
    List<Long> findCurrentCriterionIdsForShare(@Param("rubricId") Long rubricId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct rubric from LmsRubric rubric left join fetch rubric.criteria where rubric.id = :rubricId")
    Optional<LmsRubric> findByIdForUpdate(@Param("rubricId") Long rubricId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select distinct rubric from LmsRubric rubric left join fetch rubric.criteria where rubric.id = :rubricId")
    Optional<LmsRubric> findByIdForShare(@Param("rubricId") Long rubricId);

    @Query(value = """
            select score.lms_rubric_assessment_id from lms_rubric_scores score
            join lms_rubric_criteria criterion on criterion.id = score.lms_rubric_criterion_id
            where criterion.lms_rubric_id = :rubricId limit 1 for share
            """, nativeQuery = true)
    List<Long> findUseForShare(@Param("rubricId") Long rubricId);

    @Query(value = """
            select distinct criterion.lms_rubric_id from lms_rubric_scores score
            join lms_rubric_criteria criterion on criterion.id = score.lms_rubric_criterion_id
            join lms_rubrics rubric on rubric.id = criterion.lms_rubric_id
            where rubric.lms_course_id = :lmsCourseId
            """, nativeQuery = true)
    List<Long> findIdsInUse(@Param("lmsCourseId") Long lmsCourseId);
}
