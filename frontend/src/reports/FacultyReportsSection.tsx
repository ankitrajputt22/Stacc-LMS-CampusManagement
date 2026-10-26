import { Link } from 'react-router'
import { getFacultyReportCourses } from '../api/reportsApi'
import { useApiRequest } from '../api/useApiRequest'
import { actionLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatCount } from './reportFormat'

const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

/** Every offering the faculty member teaches, with or without an LMS course, with four counts and a link to its report. */
export function FacultyReportsSection() {
  const courses = useApiRequest(getFacultyReportCourses)

  let content
  if (courses.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading reports…" /></StatePanel>
  } else if (courses.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to teaching reports." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (courses.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load reports." message="Check your connection and try again." onRetry={courses.retry} />
      </StatePanel>
    )
  } else if (courses.data.length === 0) {
    content = <StatePanel><EmptyState title="No teaching courses available for reports." /></StatePanel>
  } else {
    content = (
      <ul aria-label="Teaching courses" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
        {courses.data.map((course) => (
          <li key={course.courseOfferingId} className="px-4 py-3 sm:px-5">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div className="min-w-0">
                <p className="text-sm font-semibold break-words text-ink">
                  <span className="font-mono">{course.courseCode}</span> {course.courseName}
                </p>
                <p className="text-xs text-ink-muted">
                  {course.programCode} · Semester {course.semesterNumber} · {course.academicSessionCode}
                </p>
                <dl className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
                  <div><dt className="text-xs text-ink-muted">Enrolled students</dt><dd className="font-semibold text-ink tabular-nums">{formatCount(course.activeCourseEnrollmentCount)}</dd></div>
                  <div>
                    <dt className="text-xs text-ink-muted">Assignments</dt>
                    <dd className="font-semibold text-ink tabular-nums">
                      {course.assignmentCount === null ? <span className="text-xs font-normal text-ink-muted">No open LMS course</span> : formatCount(course.assignmentCount)}
                    </dd>
                  </div>
                  <div><dt className="text-xs text-ink-muted">Finalized attendance</dt><dd className="font-semibold text-ink tabular-nums">{formatCount(course.finalizedAttendanceSessionCount)}</dd></div>
                  <div><dt className="text-xs text-ink-muted">Published results</dt><dd className="font-semibold text-ink tabular-nums">{formatCount(course.publishedResultCount)}</dd></div>
                </dl>
              </div>
              <Link to={`/reports/faculty/course-offerings/${course.courseOfferingId}`} className={`${actionLinkClasses} shrink-0 self-start md:self-center`}>
                View report<span className="sr-only">: {course.courseCode} {course.courseName}</span>
              </Link>
            </div>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <section aria-labelledby="faculty-reports-heading">
      <h2 id="faculty-reports-heading" className="text-base font-semibold text-ink">
        Teaching Reports
      </h2>
      <div className="mt-3">{content}</div>
    </section>
  )
}
