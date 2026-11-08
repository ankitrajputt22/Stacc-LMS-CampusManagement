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
import { creditsText } from '../courses/credits'
import { CourseAnnouncements } from '../courses/CourseAnnouncements'
import { CourseAssignments } from '../courses/CourseAssignments'
import { CourseContent } from '../courses/CourseContent'
import { CourseProgressCard } from '../courses/CourseProgressCard'
import { SyllabusBody } from '../courses/SyllabusBody'
import { FormattedText } from '../formatting/FormattedText'
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
      <h1 className="mt-4 text-3xl font-bold tracking-tight text-portal-navy">
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

type CourseHomeProps = {
  course: LmsCourseDetail
  announcements: ApiRequestState<LmsCourseAnnouncement[]> & { retry: () => void }
  assignments: ApiRequestState<LmsCourseAssignment[]> & { retry: () => void }
  content: ApiRequestState<LmsCourseContent> & { retry: () => void }
}

/** "Section A · Taught by Meera Iyer and Rohan Gupta", from what the API sent; empty when it sent neither. */
function teachingLine(course: LmsCourseDetail): string {
  const parts = []
  if (course.sectionCode) {
    parts.push(`Section ${course.sectionCode}`)
  }
  if (course.faculty.length > 0) {
    parts.push(`Taught by ${listFormat.format(course.faculty)}`)
  }
  return parts.join(' · ')
}

const listFormat = new Intl.ListFormat('en', { style: 'long', type: 'conjunction' })

/**
 * The course home: the official course details, then what the faculty chose students see first: the overview
 * (welcome message, progress, announcements, assignments, and modules), the modules, or the syllabus. Only what the
 * backend sent; the course navigation leads to everything else.
 */
function CourseHome({ course, announcements, assignments, content }: CourseHomeProps) {
  const teaching = teachingLine(course)
  return (
    <>
      <title>{`${course.courseName} · Stacc`}</title>
      <CourseBreadcrumb courseName={course.courseName} />

      <header className="mt-4">
        <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">
          {course.courseCode}
        </p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight break-words text-portal-navy">
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
        {teaching && <p className="mt-1 text-sm leading-6 break-words text-ink">{teaching}</p>}
      </header>

      {course.homeView === 'SYLLABUS' && (
        <section aria-labelledby="course-home-syllabus" className="mt-6 max-w-3xl">
          <h2 id="course-home-syllabus" className="mb-3 text-base font-semibold text-ink">
            Syllabus
          </h2>
          <SyllabusBody lmsCourseId={course.lmsCourseId} headingLevel={3} />
        </section>
      )}

      {course.homeView === 'MODULES' && (
        <>
          <CourseProgressCard lmsCourseId={course.lmsCourseId} />
          <CourseContent lmsCourseId={course.lmsCourseId} content={content} />
        </>
      )}

      {course.homeView === 'OVERVIEW' && (
        <>
          {course.welcomeMessage && (
            <section aria-label="Welcome" className="mt-6 max-w-2xl rounded-panel border border-line bg-surface px-4 py-3 sm:px-5">
              <FormattedText text={course.welcomeMessage} format="FORMATTED" className="text-sm leading-6 text-ink" headingLevel={2} />
            </section>
          )}
          <CourseProgressCard lmsCourseId={course.lmsCourseId} />
          <CourseAnnouncements announcements={announcements} />
          <CourseAssignments lmsCourseId={course.lmsCourseId} assignments={assignments} />
          <CourseContent lmsCourseId={course.lmsCourseId} content={content} />
        </>
      )}
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
