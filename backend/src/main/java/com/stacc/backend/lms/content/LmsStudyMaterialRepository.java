package com.stacc.backend.lms.content;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsStudyMaterialRepository extends JpaRepository<LmsStudyMaterial, Long> {

    /** The materials of one topic in these statuses, in topic order: by position, then by ID. */
    List<LmsStudyMaterial> findAllByLmsTopicIdAndStatusInOrderByPositionAscIdAsc(
            Long lmsTopicId, Collection<LmsContentStatus> statuses);

    /**
     * Every material of one LMS course, in one query, whose module, topic, and material are in the given
     * statuses, in topic order. The caller groups them by topic.
     */
    @Query("""
            select material
            from LmsStudyMaterial material
            join fetch material.lmsTopic topic
            join fetch topic.lmsModule lmsModule
            where lmsModule.lmsCourse.id = :lmsCourseId
              and lmsModule.status in :moduleStatuses
              and topic.status in :topicStatuses
              and material.status in :materialStatuses
            order by material.position, material.id
            """)
    List<LmsStudyMaterial> findAllInCourse(
            @Param("lmsCourseId") Long lmsCourseId,
            @Param("moduleStatuses") Collection<LmsContentStatus> moduleStatuses,
            @Param("topicStatuses") Collection<LmsContentStatus> topicStatuses,
            @Param("materialStatuses") Collection<LmsContentStatus> materialStatuses);

    /** One material together with its topic and module, whose course decides who may use it. */
    @Query("""
            select material
            from LmsStudyMaterial material
            join fetch material.lmsTopic topic
            join fetch topic.lmsModule
            where material.id = :materialId
            """)
    Optional<LmsStudyMaterial> findWithTopicAndModuleById(@Param("materialId") Long materialId);
}
