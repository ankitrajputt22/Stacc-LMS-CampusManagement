import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AssessmentDraftPanel } from '../ai/AssessmentDraftPanel'
import { KnowledgePanel } from '../ai/KnowledgePanel'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { useFacultyCourseContent } from '../teaching/useFacultyCourseContent'

const TEACHING = { to: '/teaching', label: 'Teaching' }
const CHANGED_ACCESS =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

const backLinkClasses =
  'inline-flex min-h-11 items-center rounded-control font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

function ToolsState({ lmsCourseId, heading = 'AI Tools', srOnly = true, children }: {
  lmsCourseId: number | null
  heading?: string
  srOnly?: boolean
  children: ReactNode
}) {
  return (
    <>
      <title>{`${heading} · Stacc`}</title>
      <Breadcrumb
        parent={lmsCourseId === null ? TEACHING : [TEACHING, { to: `/teaching/${lmsCourseId}`, label: 'Course' }]}
      />
      <h1 className={srOnly ? 'sr-only' : 'mt-4 text-2xl font-semibold tracking-tight text-ink'}>{heading}</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

function FacultyTools({ lmsCourseId }: { lmsCourseId: number | null }) {
  const course = useFacultyCourseContent(lmsCourseId)

  switch (course.status) {
    case 'loading':
      return (
        <ToolsState lmsCourseId={lmsCourseId}>
          <LoadingState label="Loading course…" />
        </ToolsState>
      )
    case 'notFound':
      return (
        <ToolsState lmsCourseId={null} heading="Teaching course unavailable" srOnly={false}>
          <p className="px-4 py-6 text-sm leading-6 text-ink-muted">
            This course could not be found, it is not one you currently teach, or its LMS course is archived.
          </p>
        </ToolsState>
      )
    case 'denied':
      return (
        <ToolsState lmsCourseId={lmsCourseId}>
          <AccessDeniedState title="You don’t have access to this course’s AI tools." message={CHANGED_ACCESS} />
        </ToolsState>
      )
    case 'failed':
      return (
        <ToolsState lmsCourseId={lmsCourseId}>
          <ErrorState title="Couldn’t load this course" message="Check your connection and try again." onRetry={course.retry} />
        </ToolsState>
      )
    case 'loaded':
      return (
        <>
          <title>{`AI Tools · ${course.data.courseName} · Stacc`}</title>
          <Breadcrumb
            parent={[TEACHING, { to: `/teaching/${course.data.lmsCourseId}`, label: course.data.courseName }]}
            current="AI Tools"
          />
          <header className="mt-4 max-w-3xl">
            <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">
              {course.data.courseCode}
              <span aria-hidden="true"> · </span>
              <span className="sr-only">, </span>
              {course.data.courseName}
            </p>
            <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">AI Tools</h1>
            <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
              Keep the course’s AI knowledge current and draft assessments to review. Students use the AI Study
              Assistant from the course page.
            </p>
          </header>
          <div className="max-w-3xl">
            <KnowledgePanel lmsCourseId={course.data.lmsCourseId} />
            <AssessmentDraftPanel lmsCourseId={course.data.lmsCourseId} />
            <Link to={`/teaching/${course.data.lmsCourseId}`} className={`mt-6 text-sm ${backLinkClasses}`}>
              Back to course
            </Link>
          </div>
        </>
      )
  }
}

/**
 * Faculty AI tools for one course they teach, at /teaching/:lmsCourseId/ai, for accounts holding FACULTY. The backend
 * checks the faculty access policy on every request. Nothing here creates, publishes, or grades an assignment.
 */
export function TeachingAiPage() {
  const { account } = useAuth()
  const { lmsCourseId } = useParams()
  const courseId = parseCourseId(lmsCourseId)

  if (!hasAnyRole(account?.roles ?? [], ['FACULTY'])) {
    return (
      <ToolsState lmsCourseId={courseId}>
        <AccessDeniedState title="AI Tools are for faculty accounts." message="If you think you should, contact the college administration." />
      </ToolsState>
    )
  }
  return <FacultyTools lmsCourseId={courseId} />
}
