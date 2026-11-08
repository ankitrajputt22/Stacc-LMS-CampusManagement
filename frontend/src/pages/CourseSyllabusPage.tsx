import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { getLmsCourse } from '../api/lmsCoursesApi'
import { getCourseSyllabus } from '../api/lmsSyllabusApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { creditsText } from '../courses/credits'
import { formatDateTime } from '../courses/dates'
import { FormattedText } from '../formatting/FormattedText'
import { Card, PageHeader } from '../ui'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function Shell({ lmsCourseId, courseName, children }: { lmsCourseId: number | null; courseName?: string; children: ReactNode }) {
  return (
    <div className="max-w-3xl">
      <title>{courseName ? `Syllabus · ${courseName} · Stacc` : 'Syllabus · Stacc'}</title>
      <PageHeader
        title="Syllabus"
        breadcrumb={
          <Breadcrumb
            parent={lmsCourseId === null ? COURSES : [COURSES, { to: `/courses/${lmsCourseId}`, label: courseName ?? 'Course' }]}
            current="Syllabus"
          />
        }
      />
      {children}
    </div>
  )
}

/**
 * /courses/:lmsCourseId/syllabus: the course's official details from the academic records, then the syllabus the
 * teachers published. Drafts never reach students. The backend checks the student's access on every request.
 */
export function CourseSyllabusPage() {
  const { account } = useAuth()
  const lmsCourseId = parseCourseId(useParams().lmsCourseId)
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const ask = isStudent && lmsCourseId !== null
  const course = useApiQuery(ask ? queryKeys.lms.course(lmsCourseId) : null, (signal) => getLmsCourse(lmsCourseId ?? 0, signal))
  const syllabus = useApiQuery(ask ? queryKeys.lms.syllabus(lmsCourseId) : null, (signal) =>
    getCourseSyllabus(lmsCourseId ?? 0, signal),
  )

  if (!isStudent) {
    return (
      <Shell lmsCourseId={lmsCourseId}>
        <StatePanel>
          <AccessDeniedState title="Course syllabi are for student accounts." message={ASK_THE_COLLEGE} />
        </StatePanel>
      </Shell>
    )
  }
  const failed = [course, syllabus].find((request) => request.status !== 'loaded' && request.status !== 'loading')
  if (failed || course.status !== 'loaded' || syllabus.status !== 'loaded') {
    return (
      <Shell lmsCourseId={lmsCourseId}>
        <StatePanel>
          {!failed && <LoadingState label="Loading syllabus…" />}
          {failed?.status === 'failed' && (
            <ErrorState title="Couldn’t load the syllabus" message="Check your connection and try again." onRetry={failed.retry} />
          )}
          {failed?.status === 'denied' && <AccessDeniedState title="You don’t have access to this course." message={ASK_THE_COLLEGE} />}
          {failed?.status === 'notFound' && (
            <EmptyState title="Course unavailable" description="This LMS course could not be found or is not available to your account." />
          )}
        </StatePanel>
      </Shell>
    )
  }

  const details = course.data
  const published = syllabus.data
  return (
    <Shell lmsCourseId={lmsCourseId} courseName={details.courseName}>
      <Card title="Course details">
        <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
          {[
            ['Course', `${details.courseCode} · ${details.courseName}`],
            ['Credits', creditsText(details.credits)],
            ['Program', `${details.programCode} · Semester ${details.semesterNumber}`],
            ['Academic session', details.academicSessionCode],
            ['Department', details.departmentName],
          ].map(([term, value]) => (
            <div key={term}>
              <dt className="text-sm text-ink-muted">{term}</dt>
              <dd className="text-sm font-medium break-words text-ink">{value}</dd>
            </div>
          ))}
        </dl>
      </Card>
      <div className="mt-6">
        {published.content === null ? (
          <StatePanel>
            <EmptyState title="No syllabus published yet." description="It appears here once your teachers publish it." />
          </StatePanel>
        ) : (
          <Card>
            <FormattedText text={published.content} format="FORMATTED" className="text-base leading-7 text-ink" headingLevel={2} />
            {published.publishedAt && (
              <p className="mt-6 border-t border-line pt-3 text-xs text-ink-muted">
                Published <time dateTime={published.publishedAt}>{formatDateTime(published.publishedAt)}</time>
              </p>
            )}
          </Card>
        )}
      </div>
    </Shell>
  )
}
