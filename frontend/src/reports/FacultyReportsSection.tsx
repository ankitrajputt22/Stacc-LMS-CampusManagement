import { Link } from 'react-router'
import { getFacultyReportCourses } from '../api/reportsApi'
import { useApiRequest } from '../api/useApiRequest'
import { primaryLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { formatCount } from './reportFormat'

const SIGN_IN_AGAIN = 'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

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
      <ul aria-label="Teaching courses" className="space-y-3">
        {courses.data.map((course) => (
          <li key={course.courseOfferingId} className="rounded-panel border border-line bg-surface p-4">
            <div className="grid gap-4 lg:grid-cols-[minmax(0,16rem)_minmax(0,1fr)_auto] lg:items-center">
              <div className="flex min-w-0 items-center gap-4">
                <span aria-hidden="true" className="flex size-20 shrink-0 items-center justify-center rounded-panel bg-sky-100 text-lg font-bold text-portal-navy">
                  {course.courseCode}
                </span>
                <div className="min-w-0">
                  <p className="text-lg font-bold break-words text-ink">{course.courseCode}</p>
                  <p className="text-[0.95rem] break-words text-ink">{course.courseName}</p>
                  <p className="text-sm text-ink-muted">
                    {course.programCode} · Semester {course.semesterNumber} · {course.academicSessionCode}
                  </p>
                </div>
              </div>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-3 sm:grid-cols-4 sm:gap-0 sm:[&>div]:border-l sm:[&>div]:border-line sm:[&>div]:px-4">
                <div><dt className="text-sm text-ink">Enrolled students</dt><dd className="text-3xl font-bold text-ink tabular-nums">{formatCount(course.activeCourseEnrollmentCount)}</dd></div>
                <div>
                  <dt className="text-sm text-ink">Assignments</dt>
                  <dd className="text-3xl font-bold text-ink tabular-nums">
                    {course.assignmentCount === null ? <span className="text-sm font-normal text-ink-muted">No open LMS course</span> : formatCount(course.assignmentCount)}
                  </dd>
                </div>
                <div><dt className="text-sm text-ink">Finalized attendance</dt><dd className="text-3xl font-bold text-ink tabular-nums">{formatCount(course.finalizedAttendanceSessionCount)}</dd></div>
                <div><dt className="text-sm text-ink">Published results</dt><dd className="text-3xl font-bold text-ink tabular-nums">{formatCount(course.publishedResultCount)}</dd></div>
              </dl>
              <Link to={`/reports/faculty/course-offerings/${course.courseOfferingId}`} className={`${primaryLinkClasses} shrink-0 self-start lg:self-center`}>
                View report<span className="sr-only">: {course.courseCode} {course.courseName}</span>
              </Link>
            </div>
          </li>
        ))}
      </ul>
    )
  }

  return (
    <section aria-labelledby="faculty-reports-heading" className="rounded-panel border border-line bg-surface p-4 sm:p-5">
      <h2 id="faculty-reports-heading" className="text-2xl font-bold text-ink">
        Teaching Reports
      </h2>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-12 rounded bg-portal-cta" />
      <div className="mt-4">{content}</div>
    </section>
  )
}
