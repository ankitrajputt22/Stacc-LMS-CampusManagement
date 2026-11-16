import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { getLmsCourse } from '../api/lmsCoursesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { courseBanner } from '../courses/courseFactsLine'
import { CourseSectionHeader } from '../courses/CourseSectionHeader'
import { creditsText } from '../courses/credits'
import { CourseOutcomesCard } from '../courses/CourseOutcomesCard'
import { SyllabusBody } from '../courses/SyllabusBody'
import { Card } from '../ui'
import type { LmsCourseDetail } from '../types/lms'

const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function Shell({ course, children }: { course?: LmsCourseDetail; children: ReactNode }) {
  return (
    <div className="max-w-4xl">
      <title>{course ? `Syllabus · ${course.courseName} · Stacc` : 'Syllabus · Stacc'}</title>
      <CourseSectionHeader course={course && courseBanner(course)} title="Syllabus" />
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

  if (!isStudent) {
    return (
      <Shell>
        <StatePanel>
          <AccessDeniedState title="Course syllabi are for student accounts." message={ASK_THE_COLLEGE} />
        </StatePanel>
      </Shell>
    )
  }
  const failed = course.status !== 'loaded' && course.status !== 'loading' ? course : undefined
  if (failed || course.status !== 'loaded') {
    return (
      <Shell>
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
  return (
    <Shell course={details}>
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
      <CourseOutcomesCard lmsCourseId={details.lmsCourseId} />
      <div className="mt-6">
        <SyllabusBody lmsCourseId={details.lmsCourseId} />
      </div>
    </Shell>
  )
}
