package com.stacc.backend.lms.content;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Search: materials of these courses whose title, original file name, or description contains the text, where the
     * material, its topic, and its module are all in these statuses (for students: published only, the chain of
     * {@link LmsContentPolicy#isVisibleToStudents(LmsStudyMaterial)}). Only these details are searched, never file
     * contents. The topic and module are loaded in the same query. The caller passes only courses the account may
     * use, never an empty collection.
     */
    @Query(value = """
            select material
            from LmsStudyMaterial material
            join fetch material.lmsTopic topic
            join fetch topic.lmsModule module
            where module.lmsCourse.id in :lmsCourseIds
              and material.status in :statuses
              and topic.status in :statuses
              and module.status in :statuses
              and (lower(material.title) like :contains escape '!'
                   or lower(material.originalFileName) like :contains escape '!'
                   or lower(coalesce(material.description, '')) like :contains escape '!')
            order by case when lower(material.title) = :exact then 0 when lower(material.title) like :prefix escape '!' then 1 else 2 end, lower(material.title), material.id
            """,
            countQuery = """
            select count(material)
            from LmsStudyMaterial material
            join material.lmsTopic topic
            join topic.lmsModule module
            where module.lmsCourse.id in :lmsCourseIds
              and material.status in :statuses
              and topic.status in :statuses
              and module.status in :statuses
              and (lower(material.title) like :contains escape '!'
                   or lower(material.originalFileName) like :contains escape '!'
                   or lower(coalesce(material.description, '')) like :contains escape '!')
            """)
    Page<LmsStudyMaterial> search(@Param("lmsCourseIds") Collection<Long> lmsCourseIds,
            @Param("statuses") Collection<LmsContentStatus> statuses, @Param("exact") String exact,
            @Param("prefix") String prefix, @Param("contains") String contains, Pageable page);
}
