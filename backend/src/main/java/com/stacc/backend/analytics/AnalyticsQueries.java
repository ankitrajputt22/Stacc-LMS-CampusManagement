package com.stacc.backend.analytics;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import com.stacc.backend.lms.assignment.LmsAssignment;
import com.stacc.backend.lms.assignment.submission.LmsSubmissionAttempt;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only lookups for analytics. Each one is bounded by the courses or the person it is asked for. */
public interface AnalyticsQueries extends Repository<LmsAssignment, Long> {

    @Query("select a from LmsAssignment a where a.lmsCourse.id in :courseIds "
            + "and a.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED and a.dueAt < :now")
    List<LmsAssignment> findPublishedDue(@Param("courseIds") Collection<Long> courseIds, @Param("now") Instant now);

    @Query("""
            select attempt from LmsSubmissionAttempt attempt join fetch attempt.submission submission join fetch submission.assignment assignment
            where assignment.lmsCourse.id in :courseIds
              and assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and attempt.attemptNumber = submission.latestAttemptNumber
              and submission.courseEnrollment.semesterEnrollment.studentProfile.userAccount.id = :accountId
            """)
    List<LmsSubmissionAttempt> findLatestAttemptsOfStudent(@Param("accountId") Long accountId, @Param("courseIds") Collection<Long> courseIds);

    @Query("""
            select attempt from LmsSubmissionAttempt attempt join fetch attempt.submission submission join fetch submission.assignment assignment
            where assignment.lmsCourse.id = :courseId
              and assignment.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
              and attempt.attemptNumber = submission.latestAttemptNumber
            """)
    List<LmsSubmissionAttempt> findLatestAttemptsOfCourse(@Param("courseId") Long lmsCourseId);

    @Query("select a from LmsAssignment a where a.lmsCourse.id = :courseId "
            + "and a.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED order by a.dueAt asc")
    List<LmsAssignment> findPublishedOfCourse(@Param("courseId") Long lmsCourseId);

    interface StatusCount {
        Long getSessionId();
        String getStatus();
        long getTotal();
    }

    @Query("""
            select record.attendanceSession.id as sessionId, cast(record.status as string) as status, count(record) as total
            from AttendanceRecord record
            where record.attendanceSession.courseOffering.id = :offeringId
              and record.attendanceSession.status = com.stacc.backend.academic.attendance.AttendanceSessionStatus.FINALIZED
            group by record.attendanceSession.id, record.status
            """)
    List<StatusCount> countFinalizedAttendance(@Param("offeringId") Long courseOfferingId);

    interface PageCount {
        Long getTopicId();
        String getTitle();
        long getCompletions();
    }

    @Query("""
            select topic.id as topicId, topic.title as title, count(completion) as completions
            from LmsTopic topic left join LmsPageCompletion completion on completion.lmsTopic = topic
            where topic.lmsModule.lmsCourse.id = :courseId and topic.required = true
              and topic.status = com.stacc.backend.lms.content.LmsContentStatus.PUBLISHED
            group by topic.id, topic.title order by topic.id
            """)
    List<PageCount> countRequiredPageCompletions(@Param("courseId") Long lmsCourseId);

    @Query("""
            select result.marksObtained, result.maxMarks from CourseResult result
            where result.courseEnrollment.semesterEnrollment.studentProfile.userAccount.id = :accountId
              and result.status = com.stacc.backend.academic.result.CourseResultStatus.PUBLISHED
            """)
    List<Object[]> findPublishedResultMarks(@Param("accountId") Long accountId);
}
