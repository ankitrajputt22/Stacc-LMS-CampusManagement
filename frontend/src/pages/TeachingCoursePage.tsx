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
import { CourseHomeSettings } from '../teaching/CourseHomeSettings'
import { useFacultyCourse } from '../teaching/useFacultyCourse'
import type { LmsFacultyCourseContent } from '../types/lms'

const TEACHING = { to: '/teaching', label: 'Teaching' }

/** A page state with the breadcrumb above it and a heading. */
function TeachingCourseState({ heading, srOnlyHeading = true, children }: { heading: string; srOnlyHeading?: boolean; children: ReactNode }) {
  return (
    <>
      <title>{`${heading} · Stacc`}</title>
      <Breadcrumb parent={TEACHING} />
      <h1 className={srOnlyHeading ? 'sr-only' : 'mt-4 text-3xl font-bold tracking-tight text-portal-navy'}>
        {heading}
      </h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

function CourseHeader({ course }: { course: LmsFacultyCourseContent }) {
  return (
    <header className="mt-4">
      <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">{course.courseCode}</p>
      <h1 className="mt-1 text-3xl font-bold tracking-tight break-words text-portal-navy">{course.courseName}</h1>
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
  )
}

type Section = { title: string; heading: string }

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
          <Breadcrumb
            parent={section.heading === '' ? TEACHING : [TEACHING, { to: `/teaching/${course.data.lmsCourseId}`, label: course.data.courseName }]}
            current={section.heading === '' ? course.data.courseName : section.heading}
          />
          {section.heading === '' ? (
            <CourseHeader course={course.data} />
          ) : (
            <h1 className="mt-4 text-3xl font-bold tracking-tight text-portal-navy">{section.heading}</h1>
          )}
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
          <CourseHomeSettings key={`${course.homeView}|${course.welcomeMessage ?? ''}`} course={course} />
        </>
      )}
    </TeachingCourseFrame>
  )
}

/** /teaching/:lmsCourseId/modules: modules and their pages, with every change the content editor offers. */
export function TeachingModulesPage() {
  return (
    <TeachingCourseFrame section={{ title: 'Modules', heading: 'Modules' }}>
      {(course, reload) => <CourseContentEditor course={course} reload={reload} />}
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
