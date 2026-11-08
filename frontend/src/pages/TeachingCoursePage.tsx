import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { creditsText } from '../courses/credits'
import { AnnouncementsEditor } from '../teaching/AnnouncementsEditor'
import { AssignmentsEditor } from '../teaching/AssignmentsEditor'
import { CourseContentEditor } from '../teaching/CourseContentEditor'
import { CourseSettings } from '../teaching/CourseSettings'
import { CopyContentCard } from '../teaching/CopyContentCard'
import { CourseHomeSettings } from '../teaching/CourseHomeSettings'
import { OutcomesEditor } from '../teaching/OutcomesEditor'
import { QuestionBankEditor } from '../teaching/QuestionBankEditor'
import { GradebookEditor } from '../teaching/GradebookEditor'
import { ManualGradeItem } from '../teaching/ManualGradeItem'
import { QuizAttemptMarking } from '../teaching/QuizAttemptMarking'
import { QuizEditor } from '../teaching/QuizEditor'
import { QuizResultsTable } from '../teaching/QuizResultsTable'
import { QuizzesEditor } from '../teaching/QuizzesEditor'
import { useFacultyCourse } from '../teaching/useFacultyCourse'
import type { LmsFacultyCourseContent } from '../types/lms'
import { PageHeader } from '../ui'

const TEACHING = { to: '/teaching', label: 'Teaching' }

/** A page state with the breadcrumb above it and a heading. */
function TeachingCourseState({ heading, srOnlyHeading = true, children }: { heading: string; srOnlyHeading?: boolean; children: ReactNode }) {
  return (
    <>
      <title>{`${heading} · Stacc`}</title>
      {srOnlyHeading ? (
        <>
          <Breadcrumb parent={TEACHING} />
          <h1 className="sr-only">{heading}</h1>
          <div className="mt-4" />
        </>
      ) : (
        <PageHeader breadcrumb={<Breadcrumb parent={TEACHING} />} title={heading} />
      )}
      <StatePanel>{children}</StatePanel>
    </>
  )
}

/** The official details of the course, from the academic records: code, program, semester, session, and credits. */
function courseLine(course: LmsFacultyCourseContent): ReactNode {
  return (
      <>
        <span className="sr-only">Program </span>
        {course.programCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        Semester {course.semesterNumber}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, Academic session </span>
        {course.academicSessionCode}
        <span aria-hidden="true"> · </span>
        <span className="sr-only">, </span>
        {creditsText(course.credits)}
      </>
  )
}

/** A page of the course: its title and heading, and for a page inside a section, that section (such as Quizzes). */
type Section = { title: string; heading: string; parent?: { path: string; label: string } }

/**
 * Loads the course the faculty member teaches once (from the shared cache) and shows its loading, missing, refused,
 * and failed states the same way on every teaching page; then the page's own part, with the course and a reload.
 */
function TeachingCourseFrame({
  section,
  children,
}: {
  section: Section
  children: (course: LmsFacultyCourseContent, reload: () => void) => ReactNode
}) {
  const { account } = useAuth()
  const { lmsCourseId } = useParams()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const course = useFacultyCourse(isFaculty ? parseCourseId(lmsCourseId) : null)

  if (!isFaculty) {
    return (
      <TeachingCourseState heading="Course">
        <AccessDeniedState
          title="This page is for faculty accounts."
          message="If you think you should, contact the college administration."
        />
      </TeachingCourseState>
    )
  }

  switch (course.status) {
    case 'loading':
      return (
        <TeachingCourseState heading="Course">
          <LoadingState label="Loading course…" />
        </TeachingCourseState>
      )
    case 'notFound':
      return (
        <TeachingCourseState heading="Teaching course unavailable" srOnlyHeading={false}>
          <p className="px-4 py-6 text-sm leading-6 text-ink-muted">
            This course could not be found, it is not one you currently teach, or its LMS course is archived.
          </p>
        </TeachingCourseState>
      )
    case 'denied':
      return (
        <TeachingCourseState heading="Course">
          <AccessDeniedState
            title="You don’t have access to manage this course."
            message="If your access changed recently, sign out and sign in again. Otherwise, contact the college administration."
          />
        </TeachingCourseState>
      )
    case 'failed':
      return (
        <TeachingCourseState heading="Course">
          <ErrorState title="Couldn’t load the course" message="Check your connection and try again." onRetry={course.retry} />
        </TeachingCourseState>
      )
    case 'loaded':
      return (
        <>
          <title>{`${section.title}${section.title === course.data.courseName ? '' : ` · ${course.data.courseName}`} · Teaching · Stacc`}</title>
          <PageHeader
            breadcrumb={
              <Breadcrumb
                parent={
                  section.heading === ''
                    ? TEACHING
                    : [
                        TEACHING,
                        { to: `/teaching/${course.data.lmsCourseId}`, label: course.data.courseName },
                        ...(section.parent
                          ? [{ to: `/teaching/${course.data.lmsCourseId}/${section.parent.path}`, label: section.parent.label }]
                          : []),
                      ]
                }
                current={section.heading === '' ? course.data.courseName : section.heading}
              />
            }
            eyebrow={course.data.courseCode}
            title={section.heading === '' ? course.data.courseName : section.heading}
            description={section.heading === '' ? courseLine(course.data) : undefined}
          />
          {children(course.data, course.refresh)}
        </>
      )
  }
}

/**
 * /teaching/:lmsCourseId, the course's home for faculty: its official details, Course settings (publish and archive),
 * and the course home students see first. Modules, announcements, assignments, files, the syllabus, and AI tools have
 * their own pages in the course navigation. The backend checks access from official teaching assignments every time.
 */
export function TeachingCoursePage() {
  return (
    <TeachingCourseFrame section={{ title: 'Course', heading: '' }}>
      {(course, reload) => (
        <>
          <CourseSettings course={course} reload={reload} />
          {/* Not keyed by the saved values: a save reloads the course, and remounting would drop "Course home saved." */}
          <CourseHomeSettings course={course} />
        </>
      )}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/modules: modules and their pages, with every change the content editor offers. */
export function TeachingModulesPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Modules', heading: 'Modules' }}>
      {(course, reload) => (
        <>
          <CopyContentCard lmsCourseId={course.lmsCourseId} reload={reload} />
          <CourseContentEditor course={course} reload={reload} />
        </>
      )}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/announcements: the course's announcements, drafts included. */
export function TeachingAnnouncementsPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Announcements', heading: 'Announcements' }}>
      {(course) => <AnnouncementsEditor lmsCourseId={course.lmsCourseId} />}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/assignments: the course's assignments, with links to their submissions. */
export function TeachingAssignmentsPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Assignments', heading: 'Assignments' }}>
      {(course) => <AssignmentsEditor lmsCourseId={course.lmsCourseId} />}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/outcomes: the course's learning outcomes. */
export function TeachingOutcomesPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Outcomes', heading: 'Course outcomes' }}>
      {(course) => <OutcomesEditor lmsCourseId={course.lmsCourseId} />}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/questions: the course's question bank. */
export function TeachingQuestionBankPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Question bank', heading: 'Question bank' }}>
      {(course) => <QuestionBankEditor lmsCourseId={course.lmsCourseId} />}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/quizzes: the course's quizzes, with New quiz. */
export function TeachingQuizzesPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Quizzes', heading: 'Quizzes' }}>
      {(course) => <QuizzesEditor lmsCourseId={course.lmsCourseId} />}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/quizzes/:quizId: one quiz with its settings and questions. */
export function TeachingQuizPage() {
  const quizId = parseCourseId(useParams().quizId)
  return (
    <TeachingCourseFrame section={{ title: 'Quiz', heading: 'Quiz', parent: { path: 'quizzes', label: 'Quizzes' } }}>
      {(course) =>
        quizId === null ? (
          <p className="text-sm text-ink-muted">This quiz address is not valid.</p>
        ) : (
          // Keyed by the quiz, so moving to another quiz starts with its own state.
          <QuizEditor key={quizId} lmsCourseId={course.lmsCourseId} quizId={quizId} />
        )
      }
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/quizzes/:quizId/results: every attempt at a quiz with its marks. */
export function TeachingQuizResultsPage() {
  const quizId = parseCourseId(useParams().quizId)
  return (
    <TeachingCourseFrame section={{ title: 'Quiz results', heading: 'Quiz results', parent: { path: 'quizzes', label: 'Quizzes' } }}>
      {(course) =>
        quizId === null ? (
          <p className="text-sm text-ink-muted">This quiz address is not valid.</p>
        ) : (
          <QuizResultsTable key={quizId} lmsCourseId={course.lmsCourseId} quizId={quizId} />
        )
      }
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/quiz-attempts/:attemptId: one attempt for marking. */
export function TeachingQuizAttemptPage() {
  const attemptId = parseCourseId(useParams().attemptId)
  return (
    <TeachingCourseFrame section={{ title: 'Mark attempt', heading: 'Mark attempt', parent: { path: 'quizzes', label: 'Quizzes' } }}>
      {(course) =>
        attemptId === null ? (
          <p className="text-sm text-ink-muted">This attempt address is not valid.</p>
        ) : (
          <QuizAttemptMarking key={attemptId} lmsCourseId={course.lmsCourseId} attemptId={attemptId} />
        )
      }
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/gradebook: every current student's marks, with publishing and manual items. */
export function TeachingGradebookPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Gradebook', heading: 'Gradebook' }}>
      {(course) => <GradebookEditor lmsCourseId={course.lmsCourseId} />}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/gradebook/items/:itemId: a manual item's marks. */
export function TeachingGradeItemPage() {
  const itemId = parseCourseId(useParams().itemId)
  return (
    <TeachingCourseFrame section={{ title: 'Grade item', heading: 'Grade item', parent: { path: 'gradebook', label: 'Gradebook' } }}>
      {(course) =>
        itemId === null ? (
          <p className="text-sm text-ink-muted">This item address is not valid.</p>
        ) : (
          <ManualGradeItem key={itemId} lmsCourseId={course.lmsCourseId} itemId={itemId} />
        )
      }
    </TeachingCourseFrame>
  )
}

