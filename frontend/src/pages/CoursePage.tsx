import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId } from '../courses/courseRoute'
import { creditsText, formatCredits } from '../courses/credits'
import { CourseAnnouncements } from '../courses/CourseAnnouncements'
import { CourseAssignments } from '../courses/CourseAssignments'
import { CourseContent } from '../courses/CourseContent'
import { useCourseAnnouncements } from '../courses/useCourseAnnouncements'
import { useCourseAssignments } from '../courses/useCourseAssignments'
import { useLmsCourse } from '../courses/useLmsCourse'
import { useLmsCourseContent } from '../courses/useLmsCourseContent'
import type { ApiRequestState } from '../api/useApiRequest'
import type {
  LmsCourseAnnouncement,
  LmsCourseAssignment,
  LmsCourseContent,
  LmsCourseDetail,
} from '../types/lms'

const COURSES_PATH = '/courses'
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

const backLinkClasses =
  'inline-flex min-h-11 items-center rounded-control font-medium text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

/** "Courses / Course name": the way back to the list, and where the student is. */
function CourseBreadcrumb({ courseName }: { courseName?: string }) {
  return <Breadcrumb parent={{ to: COURSES_PATH, label: 'Courses' }} current={courseName} />
}

/** A page state with the breadcrumb above it and a heading for screen readers. */
function CourseState({ children }: { children: ReactNode }) {
  return (
    <>
      <title>Course · Stacc</title>
      <CourseBreadcrumb />
      <h1 className="sr-only">Course</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

function CourseUnavailable() {
  return (
    <>
      <title>Course unavailable · Stacc</title>
      <CourseBreadcrumb />
      <h1 className="mt-4 text-2xl font-semibold tracking-tight text-ink">
        Course unavailable
      </h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        This LMS course could not be found or is not available to your account.
      </p>
      <Link to={COURSES_PATH} className={`mt-4 text-sm ${backLinkClasses}`}>
        Back to Courses
      </Link>
    </>
  )
}

function InformationRow({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="grid gap-0.5 px-4 py-3 sm:grid-cols-[11rem_minmax(0,1fr)] sm:gap-4 sm:px-5">
      <dt className="text-sm text-ink-muted">{term}</dt>
      <dd className="text-sm break-words text-ink">{children}</dd>
    </div>
  )
}

type CourseHomeProps = {
  course: LmsCourseDetail
  announcements: ApiRequestState<LmsCourseAnnouncement[]> & { retry: () => void }
  assignments: ApiRequestState<LmsCourseAssignment[]> & { retry: () => void }
  content: ApiRequestState<LmsCourseContent> & { retry: () => void }
}

/**
 * The course home: the course's academic information, its published announcements and assignments, then
 * its published content. Only what the backend sent.
 */
function CourseHome({ course, announcements, assignments, content }: CourseHomeProps) {
  return (
    <>
      <title>{`${course.courseName} · Stacc`}</title>
      <CourseBreadcrumb courseName={course.courseName} />

      <header className="mt-4">
        <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">
          {course.courseCode}
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight break-words text-ink">
          {course.courseName}
        </h1>
        <p className="mt-2 text-sm leading-6 break-words text-ink-muted">
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
        </p>
      </header>

      <section
        aria-labelledby="course-ai-heading"
        className="mt-6 max-w-2xl rounded-panel border border-line bg-surface px-4 py-3 sm:px-5"
      >
        <h2 id="course-ai-heading" className="text-base font-semibold text-ink">
          AI Study Assistant
        </h2>
        <p className="mt-1 text-sm leading-6 text-ink-muted">
          Ask questions, summarise topics, and practise, based on this course’s published materials.
        </p>
        <Link to={`/courses/${course.lmsCourseId}/ai`} className={`mt-1 text-sm ${backLinkClasses}`}>
          Open AI Study Assistant
        </Link>
      </section>

      <section aria-labelledby="course-academic-information" className="mt-8 max-w-2xl">
        <h2
          id="course-academic-information"
          className="text-base font-semibold text-ink"
        >
          Academic information
        </h2>
        <dl className="mt-3 divide-y divide-line rounded-panel border border-line bg-surface">
          <InformationRow term="Program">
            {course.programName}{' '}
            <span className="text-ink-muted">({course.programCode})</span>
          </InformationRow>
          <InformationRow term="Department">
            {course.departmentName}{' '}
            <span className="text-ink-muted">({course.departmentCode})</span>
          </InformationRow>
          <InformationRow term="Semester">{course.semesterNumber}</InformationRow>
          <InformationRow term="Academic session">
            {course.academicSessionCode}
          </InformationRow>
          <InformationRow term="Credits">{formatCredits(course.credits)}</InformationRow>
        </dl>
      </section>

      <CourseAnnouncements announcements={announcements} />
      <CourseAssignments lmsCourseId={course.lmsCourseId} assignments={assignments} />
      <CourseContent content={content} />
    </>
  )
}

function StudentCourse({ lmsCourseId }: { lmsCourseId: number | null }) {
  // All the requests start together. Each section below the course details has its own states.
  const course = useLmsCourse(lmsCourseId)
  const announcements = useCourseAnnouncements(lmsCourseId)
  const assignments = useCourseAssignments(lmsCourseId)
  const content = useLmsCourseContent(lmsCourseId)

  switch (course.status) {
    case 'loading':
      return (
        <CourseState>
          <LoadingState label="Loading course…" />
        </CourseState>
      )
    case 'notFound':
      return <CourseUnavailable />
    case 'denied':
      return (
        <CourseState>
          <AccessDeniedState
            title="You don’t have access to this course."
            message={ASK_THE_COLLEGE}
          />
        </CourseState>
      )
    case 'failed':
      return (
        <CourseState>
          <ErrorState
            title="Couldn’t load this course"
            message="Check your connection and try again."
            onRetry={course.retry}
          />
        </CourseState>
      )
    case 'loaded':
      return (
        <CourseHome course={course.data} announcements={announcements} assignments={assignments} content={content} />
      )
  }
}

/**
 * One LMS course for the signed-in student, at /courses/:lmsCourseId. Arriving from the list
 * proves nothing: the backend checks access again for every request. Accounts without the
 * student role see an explanation instead, and no request is made.
 */
export function CoursePage() {
  const { account } = useAuth()
  const { lmsCourseId } = useParams()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])

  if (!isStudent) {
    return (
      <CourseState>
        <AccessDeniedState
          title="Courses are for student accounts."
          message={ASK_THE_COLLEGE}
        />
      </CourseState>
    )
  }

  return <StudentCourse lmsCourseId={parseCourseId(lmsCourseId)} />
}
