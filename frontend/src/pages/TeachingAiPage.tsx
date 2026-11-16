import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AssessmentDraftPanel } from '../ai/AssessmentDraftPanel'
import { BankDraftPanel } from '../ai/BankDraftPanel'
import { QuizDraftPanel } from '../ai/QuizDraftPanel'
import { KnowledgePanel } from '../ai/KnowledgePanel'
import { TeacherDocumentsPanel } from '../ai/TeacherDocumentsPanel'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { Card, LinkTabs, textLinkClasses } from '../ui'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { teachingCourseBanner } from '../courses/courseFactsLine'
import { CourseSectionHeader } from '../courses/CourseSectionHeader'
import { useFacultyCourseContent } from '../teaching/useFacultyCourseContent'

const TEACHING = { to: '/teaching', label: 'Teaching' }
const CHANGED_ACCESS =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

const backLinkClasses =
  'inline-flex min-h-11 items-center rounded-control font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

function ToolsState({ lmsCourseId, heading = 'Teacher AI', srOnly = true, children }: {
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
      <h1 className={srOnly ? 'sr-only' : 'mt-4 text-3xl font-bold tracking-tight text-portal-navy'}>{heading}</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

/** The Teacher AI sections (v0.14.6), each its own address so it can be bookmarked. */
const SECTIONS = [
  { key: '', label: 'Knowledge' },
  { key: 'bank', label: 'Question bank' },
  { key: 'quizzes', label: 'Quizzes' },
  { key: 'assignments', label: 'Assignments' },
  { key: 'more', label: 'More' },
] as const

/** Where the AI help that lives on other teaching pages is. */
function MoreAi({ lmsCourseId }: { lmsCourseId: number }) {
  const links = [
    { to: `/teaching/${lmsCourseId}/questions`, label: 'Question bank', text: 'Suggest a model answer on any short-answer or descriptive question.' },
    { to: `/teaching/${lmsCourseId}/rubrics`, label: 'Rubrics', text: 'Suggest a rubric for an assignment and open it in the rubric form.' },
    { to: `/teaching/${lmsCourseId}/papers`, label: 'Exam papers', text: 'Check what the bank has for a blueprint and draft the missing questions.' },
    { to: `/teaching/${lmsCourseId}/files`, label: 'Files', text: 'Keep a file out of AI knowledge without hiding it from students.' },
  ]
  return (
    <Card title="AI on other pages">
      <ul className="space-y-3 text-sm">
        {links.map((link) => (
          <li key={link.to}>
            <Link to={link.to} className={textLinkClasses}>{link.label}</Link>
            <span className="block text-ink-muted">{link.text}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}

function FacultyTools({ lmsCourseId, section }: { lmsCourseId: number | null; section: string }) {
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
          <AccessDeniedState title="You don’t have access to this course’s Teacher AI." message={CHANGED_ACCESS} />
        </ToolsState>
      )
    case 'failed':
      return (
        <ToolsState lmsCourseId={lmsCourseId}>
          <ErrorState title="Couldn’t load this course" message="Check your connection and try again." onRetry={course.retry} />
        </ToolsState>
      )
    case 'loaded': {
      const root = `/teaching/${course.data.lmsCourseId}/ai`
      const current = SECTIONS.find((choice) => choice.key === section) ?? SECTIONS[0]
      return (
        <>
          <title>{`Teacher AI · ${current.label} · ${course.data.courseName} · Stacc`}</title>
          <CourseSectionHeader
            course={teachingCourseBanner(course.data)}
            title="Teacher AI"
            description="AI help for this course, built only from its published content and your teacher-only documents. Everything AI makes is a draft for you to review; students use the AI Study Assistant from the course page."
          />
          <LinkTabs
            label="Teacher AI sections"
            tabs={SECTIONS.map((choice) => ({ to: choice.key ? `${root}/${choice.key}` : root, label: choice.label, end: true }))}
          />
          <div className="max-w-3xl">
            {current.key === '' && (
              <>
                <KnowledgePanel lmsCourseId={course.data.lmsCourseId} />
                <TeacherDocumentsPanel lmsCourseId={course.data.lmsCourseId} />
              </>
            )}
            {current.key === 'bank' && <BankDraftPanel lmsCourseId={course.data.lmsCourseId} />}
            {current.key === 'quizzes' && <QuizDraftPanel lmsCourseId={course.data.lmsCourseId} />}
            {current.key === 'assignments' && <AssessmentDraftPanel lmsCourseId={course.data.lmsCourseId} />}
            {current.key === 'more' && <MoreAi lmsCourseId={course.data.lmsCourseId} />}
            <Link to={`/teaching/${course.data.lmsCourseId}`} className={`mt-6 text-sm ${backLinkClasses}`}>
              Back to course
            </Link>
          </div>
        </>
      )
    }
  }
}

/**
 * Teacher AI (v0.14.6) for one course they teach, at /teaching/:lmsCourseId/ai and its sections (bank, quizzes,
 * assignments, more), for accounts holding FACULTY. The backend
 * checks the faculty access policy on every request. Nothing here creates, publishes, or grades an assignment.
 */
export function TeachingAiPage() {
  const { account } = useAuth()
  const { lmsCourseId, section } = useParams()
  const courseId = parseCourseId(lmsCourseId)

  if (!hasAnyRole(account?.roles ?? [], ['FACULTY'])) {
    return (
      <ToolsState lmsCourseId={courseId}>
        <AccessDeniedState title="Teacher AI is for faculty accounts." message="If you think you should, contact the college administration." />
      </ToolsState>
    )
  }
  return <FacultyTools lmsCourseId={courseId} section={section ?? ''} />
}
