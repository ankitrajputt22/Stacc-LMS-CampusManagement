import { type ReactNode, useMemo, useState } from 'react'
import { useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AiMessage } from '../ai/AiMessage'
import { AskPanel } from '../ai/AskPanel'
import { FlashcardsPanel } from '../ai/FlashcardsPanel'
import { MasteryPanel } from '../ai/MasteryPanel'
import { RecommendationsPanel } from '../ai/RecommendationsPanel'
import { StudyPlanPanel } from '../ai/StudyPlanPanel'
import { MyNotesPanel } from '../ai/MyNotesPanel'
import { SavedAnswersPanel } from '../ai/SavedAnswersPanel'
import type { CourseTopicChoice } from '../ai/CourseTopicOptions'
import { PracticePanel } from '../ai/PracticePanel'
import { SummaryPanel, type SummaryChoices } from '../ai/SummaryPanel'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { courseBanner } from '../courses/courseFactsLine'
import { CourseSectionHeader } from '../courses/CourseSectionHeader'
import { getCourseAssignments } from '../api/lmsAssignmentsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useLmsCourse } from '../courses/useLmsCourse'
import { useLmsCourseContent } from '../courses/useLmsCourseContent'
import type { LmsCourseContent, LmsCourseDetail } from '../types/lms'
import { ButtonLink, LinkTabs, PageHeader } from '../ui'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function AiState({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <>
      <title>AI Study Assistant · Stacc</title>
      <PageHeader
        breadcrumb={<Breadcrumb parent={lmsCourseId === null ? COURSES : [COURSES, { to: `/courses/${lmsCourseId}`, label: 'Course' }]} />}
        title="AI Study Assistant"
      />
      <StatePanel>{children}</StatePanel>
    </>
  )
}

function publishedTopics(content: LmsCourseContent): CourseTopicChoice[] {
  return content.modules.flatMap((module) =>
    module.topics.map((topic) => ({ id: topic.id, title: topic.title, moduleId: module.id, moduleTitle: module.title })),
  )
}

function summaryChoices(content: LmsCourseContent): SummaryChoices {
  return {
    modules: content.modules.filter((module) => module.topics.length > 0).map((module) => ({ id: module.id, title: module.title })),
    topics: content.modules.flatMap((module) => module.topics.map((topic) => ({ id: topic.id, title: topic.title, moduleTitle: module.title }))),
    files: content.modules.flatMap((module) => module.topics.flatMap((topic) =>
      topic.materials.map((material) => ({ id: material.id, title: material.title, pageTitle: topic.title })))),
  }
}

const STUDY_SECTIONS = [
  { key: '', label: 'Overview' },
  { key: 'ask', label: 'Ask' },
  { key: 'summaries', label: 'Summaries' },
  { key: 'practice', label: 'Practice' },
  { key: 'flashcards', label: 'Flashcards' },
  { key: 'notes', label: 'My notes' },
] as const

function Assistant({ course, content }: { course: LmsCourseDetail; content: LmsCourseContent }) {
  const { section } = useParams()
  const root = `/courses/${course.lmsCourseId}/ai`
  const current = STUDY_SECTIONS.find((choice) => choice.key === (section ?? '')) ?? STUDY_SECTIONS[0]
  const assignments = useApiQuery(queryKeys.lms.courseAssignments(course.lmsCourseId),
    (signal) => getCourseAssignments(course.lmsCourseId, signal))
  const [openedAt] = useState(() => Date.now())
  const dueAssignments = assignments.status === 'loaded'
    ? assignments.data.filter((assignment) => new Date(assignment.dueAt).getTime() > openedAt)
      .map((assignment) => ({ id: assignment.id, title: assignment.title }))
    : []
  const topics = useMemo(() => publishedTopics(content), [content])
  return (
    <div className="max-w-5xl">
      <title>{`AI Study Assistant · ${course.courseName} · Stacc`}</title>
      <CourseSectionHeader
        course={courseBanner(course)}
        title="AI Study Assistant"
        description="Ask, summarise, practise, and plan from your published course materials."
      />
      <LinkTabs
        label="Study sections"
        tabs={STUDY_SECTIONS.map((choice) => ({ to: choice.key ? `${root}/${choice.key}` : root, label: choice.label, end: true }))}
      />
      <div>
        <AiMessage tone="warning">
          AI answers may contain mistakes. Check the cited course sources.
        </AiMessage>
        {current.key === '' && (
          <>
            <RecommendationsPanel lmsCourseId={course.lmsCourseId} />
            <MasteryPanel lmsCourseId={course.lmsCourseId} />
            <StudyPlanPanel lmsCourseId={course.lmsCourseId} />
          </>
        )}
        {current.key === 'ask' && (
          <>
            <AskPanel
              lmsCourseId={course.lmsCourseId}
              modules={content.modules.filter((module) => module.topics.length > 0).map((module) => ({ id: module.id, title: module.title }))}
              assignments={dueAssignments}
            />
            <SavedAnswersPanel lmsCourseId={course.lmsCourseId} />
          </>
        )}
        {current.key === 'summaries' && <SummaryPanel lmsCourseId={course.lmsCourseId} choices={summaryChoices(content)} />}
        {current.key === 'practice' && <PracticePanel lmsCourseId={course.lmsCourseId} topics={topics} />}
        {current.key === 'flashcards' && <FlashcardsPanel lmsCourseId={course.lmsCourseId} choices={summaryChoices(content)} />}
        {current.key === 'notes' && <MyNotesPanel lmsCourseId={course.lmsCourseId} />}
      </div>
    </div>
  )
}

function StudentAssistant({ lmsCourseId }: { lmsCourseId: number | null }) {
  const course = useLmsCourse(lmsCourseId)
  const content = useLmsCourseContent(lmsCourseId)

  if (course.status === 'notFound' || content.status === 'notFound') {
    return (
      <>
        <title>Course unavailable · Stacc</title>
        <PageHeader
          breadcrumb={<Breadcrumb parent={COURSES} />}
          title="Course unavailable"
          description="This course could not be found or is not available to your account."
          actions={
            <ButtonLink to={COURSES.to} variant="secondary">
              Back to Courses
            </ButtonLink>
          }
        />
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
