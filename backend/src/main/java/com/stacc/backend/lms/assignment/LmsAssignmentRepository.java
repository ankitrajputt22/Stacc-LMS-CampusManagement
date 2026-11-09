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

    /** One course's assignments in these statuses, earliest deadline first. */
    List<LmsAssignment> findAllByLmsCourseIdAndStatusInOrderByDueAtAscIdAsc(
            Long lmsCourseId, Collection<LmsContentStatus> statuses);

    /**
     * The assignments of several courses in one status whose deadline is at or after a moment, one page of them, in
     * the page's order, with the total count of all of them.
     */
    Page<LmsAssignment> findByLmsCourseIdInAndStatusAndDueAtGreaterThanEqual(
            Collection<Long> lmsCourseIds, LmsContentStatus status, Instant dueFrom, Pageable page);

    /** The assignments of several courses in these statuses, earliest deadline first. */
    List<LmsAssignment> findAllByLmsCourseIdInAndStatusInOrderByDueAtAscIdAsc(
            Collection<Long> lmsCourseIds, Collection<LmsContentStatus> statuses);

    /**
     * One assignment, only if it belongs to this LMS course, loaded together with the course and its
     * catalog entry, which the assignment page shows.
     */
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

    /**
     * The assignments of several courses in these statuses whose deadline is at or after a moment, one page of them,
     * in the page's order, with the total count of all of them.
     */
    Page<LmsAssignment> findByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqual(
            Collection<Long> lmsCourseIds, Collection<LmsContentStatus> statuses, Instant dueFrom, Pageable page);

    /**
     * The assignments of several courses in these statuses whose deadline is at or after one moment and before another,
     * earliest deadline first. The end is exclusive, so consecutive ranges never share a deadline.
     */
    List<LmsAssignment> findAllByLmsCourseIdInAndStatusInAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAscIdAsc(
            Collection<Long> lmsCourseIds, Collection<LmsContentStatus> statuses, Instant dueFrom, Instant dueBefore);

    /**
     * Search: assignments of these courses in these statuses whose title contains the text ({@code contains} is an
     * escaped, lower-case {@code LIKE} pattern). Exact titles first, then titles that start with the text, then the
     * rest, each by title. The caller passes only courses the account may use, never an empty collection.
     */
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

    /** Reports: how many assignments of each of these LMS courses are in each of these statuses, in one grouped query. */
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
