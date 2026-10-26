import { useMemo } from 'react'
import { useParams } from 'react-router'
import { getFacultyCourseReport } from '../api/reportsApi'
import { useApiRequest } from '../api/useApiRequest'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { ReportSection, ReportStats } from '../reports/ReportStats'
import type { FacultyCourseReport } from '../types/reports'

function assignmentNote(report: FacultyCourseReport['assignments']): string | undefined {
  if (report.available) {
    return undefined
  }
  return report.lmsCourseStatus === 'ARCHIVED'
    ? 'The LMS course is archived, so its assignments are not counted.'
    : 'This offering has no LMS course yet, so there are no assignments to count.'
}

function Report({ report }: { report: FacultyCourseReport }) {
  const { course, enrollment, assignments, attendance, results } = report
  return (
    <>
      <p className="mt-1 text-sm break-words text-ink">
        <span className="font-mono">{course.courseCode}</span> {course.courseName}
      </p>
      <p className="text-sm text-ink-muted">
        {course.programCode} · Semester {course.semesterNumber} · {course.academicSessionCode} · {course.credits} credits
      </p>
      <div className="mt-5 grid gap-4 lg:grid-cols-2">
        <ReportSection id="report-enrollment" title="Enrollment" note="Course enrollments by status.">
          <ReportStats label="Enrollment" items={[
            { label: 'Enrolled', value: enrollment.enrolledCount },
            { label: 'Completed', value: enrollment.completedCount },
            { label: 'Withdrawn', value: enrollment.withdrawnCount },
          ]} />
        </ReportSection>
        <ReportSection id="report-assignments" title="Assignments" note={assignmentNote(assignments)}>
          <ReportStats label="Assignments" items={[
            { label: 'Draft assignments', value: assignments.draftAssignmentCount },
            { label: 'Published assignments', value: assignments.publishedAssignmentCount },
            { label: 'Submissions received', value: assignments.submissionCount },
            { label: 'Needs grading', value: assignments.needsGradingCount },
          ]} />
          {assignments.available && (
            <p className="mt-3 text-xs text-ink-muted">
              Submissions count each student once per published assignment. Needs grading counts submissions whose latest attempt has no grade.
            </p>
          )}
        </ReportSection>
        <ReportSection id="report-attendance" title="Attendance" note="Present and absent records come from finalized sessions only.">
          <ReportStats label="Attendance" items={[
            { label: 'Open sessions', value: attendance.openAttendanceSessionCount },
            { label: 'Finalized sessions', value: attendance.finalizedAttendanceSessionCount },
            { label: 'Present records', value: attendance.presentRecordCount },
            { label: 'Absent records', value: attendance.absentRecordCount },
          ]} />
        </ReportSection>
        <ReportSection id="report-results" title="Results" note="Official results by status.">
          <ReportStats label="Results" items={[
            { label: 'Draft results', value: results.draftResultCount },
            { label: 'Published results', value: results.publishedResultCount },
          ]} />
        </ReportSection>
      </div>
    </>
  )
}

/**
 * The report of one course offering at /reports/faculty/course-offerings/:courseOfferingId, for the faculty member who
 * actively teaches it. Another offering and a missing one look the same: "Course report unavailable".
 */
export function FacultyCourseReportPage() {
  const { courseOfferingId: param } = useParams()
  const courseOfferingId = param && /^[1-9]\d{0,17}$/.test(param) ? Number(param) : null
  const load = useMemo(
    () => (courseOfferingId === null ? null : (signal: AbortSignal) => getFacultyCourseReport(courseOfferingId, signal)),
    [courseOfferingId],
  )
  const request = useApiRequest(load)

  let content
  if (courseOfferingId === null || request.status === 'notFound') {
    content = <StatePanel><EmptyState title="Course report unavailable" description="You can see reports only for course offerings you teach." /></StatePanel>
  } else if (request.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading course report…" /></StatePanel>
  } else if (request.status === 'denied') {
    content = (
      <StatePanel>
        <AccessDeniedState
          title="You don’t have access to teaching reports."
          message="If your access changed recently, sign out and sign in again. Otherwise, contact the college administration."
        />
      </StatePanel>
    )
  } else if (request.status !== 'loaded') {
    content = <StatePanel><ErrorState title="Couldn’t load the course report." message="Check your connection and try again." onRetry={request.retry} /></StatePanel>
  } else {
    content = <Report report={request.data} />
  }

  const code = request.status === 'loaded' ? request.data.course.courseCode : undefined
  return (
    <>
      <title>{code ? `${code} · Course Report · Stacc` : 'Course Report · Stacc'}</title>
      <Breadcrumb parent={{ to: '/reports', label: 'Reports' }} current={code} />
      <h1 className="mt-3 text-2xl font-semibold tracking-tight text-ink">Course Report</h1>
      <div className="mt-2">{content}</div>
    </>
  )
}
