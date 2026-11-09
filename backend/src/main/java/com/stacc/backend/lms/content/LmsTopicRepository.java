package com.stacc.backend.lms.content;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsTopicRepository extends JpaRepository<LmsTopic, Long> {

    /** The topics of one module in these statuses, in module order: by position, then by ID. */
    List<LmsTopic> findAllByLmsModuleIdAndStatusInOrderByPositionAscIdAsc(
            Long lmsModuleId, Collection<LmsContentStatus> statuses);

    /**
     * Every topic of one LMS course, in one query, whose module and topic are in the given statuses,
     * in module order: by position, then by ID. The caller groups them by module.
     */
    @Query("""
            select topic
            from LmsTopic topic
            join fetch topic.lmsModule lmsModule
            where lmsModule.lmsCourse.id = :lmsCourseId
              and lmsModule.status in :moduleStatuses
              and topic.status in :topicStatuses
            order by topic.position, topic.id
            """)
    List<LmsTopic> findAllInCourse(
            @Param("lmsCourseId") Long lmsCourseId,
            @Param("moduleStatuses") Collection<LmsContentStatus> moduleStatuses,
            @Param("topicStatuses") Collection<LmsContentStatus> topicStatuses);

    /** One topic together with its module, whose course decides who may change the topic. */
    @Query("select topic from LmsTopic topic join fetch topic.lmsModule where topic.id = :topicId")
    Optional<LmsTopic> findWithModuleById(@Param("topicId") Long topicId);
}
