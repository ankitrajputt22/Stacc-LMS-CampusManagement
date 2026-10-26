import { type ReactNode, useMemo } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AiMessage } from '../ai/AiMessage'
import { AskPanel } from '../ai/AskPanel'
import type { CourseTopicChoice } from '../ai/CourseTopicOptions'
import { PracticePanel } from '../ai/PracticePanel'
import { TopicSummaryPanel } from '../ai/TopicSummaryPanel'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { useLmsCourse } from '../courses/useLmsCourse'
import { useLmsCourseContent } from '../courses/useLmsCourseContent'
import type { LmsCourseContent, LmsCourseDetail } from '../types/lms'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

const backLinkClasses =
  'inline-flex min-h-11 items-center rounded-control font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

function AiState({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <>
      <title>AI Study Assistant · Stacc</title>
      <Breadcrumb
        parent={lmsCourseId === null ? COURSES : [COURSES, { to: `/courses/${lmsCourseId}`, label: 'Course' }]}
      />
      <h1 className="sr-only">AI Study Assistant</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

/** The published topics in course order, for the topic choices. Only what the backend sent as published. */
function publishedTopics(content: LmsCourseContent): CourseTopicChoice[] {
  return content.modules.flatMap((module) =>
    module.topics.map((topic) => ({ id: topic.id, title: topic.title, moduleId: module.id, moduleTitle: module.title })),
  )
}

function Assistant({ course, content }: { course: LmsCourseDetail; content: LmsCourseContent }) {
  const topics = useMemo(() => publishedTopics(content), [content])
  return (
    <>
      <title>{`AI Study Assistant · ${course.courseName} · Stacc`}</title>
      <Breadcrumb
        parent={[COURSES, { to: `/courses/${course.lmsCourseId}`, label: course.courseName }]}
        current="AI Study Assistant"
      />
      <header className="mt-4 max-w-3xl">
        <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">
          {course.courseCode}
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          {course.courseName}
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">AI Study Assistant</h1>
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          Ask questions based on your published course materials.
        </p>
      </header>
      <div className="max-w-3xl">
        <AiMessage tone="warning" className="mt-4">
          AI answers may contain mistakes. Check the cited course sources.
        </AiMessage>
        <AskPanel lmsCourseId={course.lmsCourseId} />
        <TopicSummaryPanel lmsCourseId={course.lmsCourseId} topics={topics} />
        <PracticePanel lmsCourseId={course.lmsCourseId} topics={topics} />
        <Link to={`/courses/${course.lmsCourseId}`} className={`mt-6 text-sm ${backLinkClasses}`}>
          Back to Course
        </Link>
      </div>
    </>
  )
}

function StudentAssistant({ lmsCourseId }: { lmsCourseId: number | null }) {
  const course = useLmsCourse(lmsCourseId)
  const content = useLmsCourseContent(lmsCourseId)

  if (course.status === 'notFound' || content.status === 'notFound') {
    return (
      <>
        <title>Course unavailable · Stacc</title>
        <Breadcrumb parent={COURSES} />
        <h1 className="mt-4 text-2xl font-semibold tracking-tight text-ink">Course unavailable</h1>
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          This course could not be found or is not available to your account.
        </p>
        <Link to={COURSES.to} className={`mt-4 text-sm ${backLinkClasses}`}>
          Back to Courses
        </Link>
      </>
    )
  }
  if (course.status === 'denied' || content.status === 'denied') {
    return (
      <AiState lmsCourseId={lmsCourseId}>
        <AccessDeniedState title="You don’t have access to this course." message={ASK_THE_COLLEGE} />
      </AiState>
    )
  }
  if (course.status === 'failed' || content.status === 'failed') {
    return (
      <AiState lmsCourseId={lmsCourseId}>
        <ErrorState
          title="Couldn’t load this course"
          message="Check your connection and try again."
          onRetry={() => {
            course.retry()
            content.retry()
          }}
        />
      </AiState>
    )
  }
  if (course.status !== 'loaded' || content.status !== 'loaded') {
    return (
      <AiState lmsCourseId={lmsCourseId}>
        <LoadingState label="Loading course…" />
      </AiState>
    )
  }
  return <Assistant course={course.data} content={content.data} />
}

/**
 * The AI Study Assistant of one course, at /courses/:lmsCourseId/ai, for accounts holding STUDENT. The backend checks
 * the course again on every request; answers are not kept once the page is left, and there is no chat history.
 */
export function CourseAiPage() {
  const { account } = useAuth()
  const { lmsCourseId } = useParams()
  const courseId = parseCourseId(lmsCourseId)

  if (!hasAnyRole(account?.roles ?? [], ['STUDENT'])) {
    return (
      <AiState lmsCourseId={courseId}>
        <AccessDeniedState title="The AI Study Assistant is for student accounts." message={ASK_THE_COLLEGE} />
      </AiState>
    )
  }
  return <StudentAssistant lmsCourseId={courseId} />
}
