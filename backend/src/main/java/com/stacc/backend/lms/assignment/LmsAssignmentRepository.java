package com.stacc.backend.lms.assignment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.lms.content.LmsContentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsAssignmentRepository extends JpaRepository<LmsAssignment, Long> {

    List<LmsAssignment> findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(
            Long lmsCourseId, Collection<LmsContentStatus> statuses);

    Page<LmsAssignment> findByLmsCourseIdInAndStatusAndDueAtGreaterThanEqual(
            Collection<Long> lmsCourseIds, LmsContentStatus status, Instant dueFrom, Pageable page);

    List<LmsAssignment> findAllByLmsCourseIdInAndStatusInOrderByDueAtAscIdAsc(
            Collection<Long> lmsCourseIds, Collection<LmsContentStatus> statuses);

    @Query("""
            select assignment
            from LmsAssignment assignment
            join fetch assignment.lmsCourse lmsCourse
            join fetch lmsCourse.courseOffering courseOffering
            join fetch courseOffering.course
            where assignment.id = :assignmentId
              and lmsCourse.id = :lmsCourseId
            """)
    Optional<LmsAssignment> findWithCourseByIdAndLmsCourseId(
            @Param("assignmentId") Long assignmentId, @Param("lmsCourseId") Long lmsCourseId);

    Page<LmsAssignment> findByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqual(
            Collection<Long> lmsCourseIds, Collection<LmsContentStatus> statuses, Instant dueFrom, Pageable page);

    List<LmsAssignment> findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
            Collection<Long> lmsCourseIds, Collection<LmsContentStatus> statuses, Instant dueFrom, Instant dueBefore);

    @Query(value = """
            select assignment
            from LmsAssignment assignment
            where assignment.lmsCourse.id in :lmsCourseIds
              and assignment.status in :statuses
              and lower(assignment.title) like :contains escape '!'
            order by case when lower(assignment.title) = :exact then 0 when lower(assignment.title) like :prefix escape '!' then 1 else 2 end, lower(assignment.title), assignment.id
            """,
            countQuery = """
            select count(assignment)
            from LmsAssignment assignment
            where assignment.lmsCourse.id in :lmsCourseIds
              and assignment.status in :statuses
              and lower(assignment.title) like :contains escape '!'
            """)
    Page<LmsAssignment> search(@Param("lmsCourseIds") Collection<Long> lmsCourseIds,
            @Param("statuses") Collection<LmsContentStatus> statuses, @Param("exact") String exact,
            @Param("prefix") String prefix, @Param("contains") String contains, Pageable page);

    @Query("""
            select assignment.lmsCourse.id as lmsCourseId, assignment.status as status, count(assignment) as total
            from LmsAssignment assignment
            where assignment.lmsCourse.id in :lmsCourseIds
              and assignment.status in :statuses
            group by assignment.lmsCourse.id, assignment.status
            """)
    List<CourseStatusCount> countByCourseAndStatus(@Param("lmsCourseIds") Collection<Long> lmsCourseIds,
            @Param("statuses") Collection<LmsContentStatus> statuses);

    interface CourseStatusCount {
        Long getLmsCourseId();

        LmsContentStatus getStatus();

        Long getTotal();
    }
}
