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
import { CourseContentEditor } from '../teaching/CourseContentEditor'
import { StatusTag } from '../teaching/StatusTag'
import { useFacultyCourseContent } from '../teaching/useFacultyCourseContent'
import type { LmsFacultyCourseContent } from '../types/lms'

const TEACHING = { to: '/teaching', label: 'Teaching' }

/** A page state with the breadcrumb above it and a heading. */
function TeachingCourseState({ heading, srOnlyHeading = true, children }: { heading: string; srOnlyHeading?: boolean; children: ReactNode }) {
  return (
    <>
      <title>{`${heading} · Stacc`}</title>
      <Breadcrumb parent={TEACHING} />
      <h1 className={srOnlyHeading ? 'sr-only' : 'mt-4 text-2xl font-semibold tracking-tight text-ink'}>
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
      <h1 className="mt-1 text-2xl font-semibold tracking-tight break-words text-ink">{course.courseName}</h1>
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
      <p className="mt-2 flex flex-wrap items-center gap-2 text-sm text-ink-muted">
        <span aria-hidden="true">LMS course:</span>
        <StatusTag status={course.lmsCourseStatus} label="LMS course" />
        {course.lmsCourseStatus === 'DRAFT' && <span>Students can’t open this course until it is published.</span>}
      </p>
    </header>
  )
}

function FacultyCourse({ lmsCourseId }: { lmsCourseId: number | null }) {
  const course = useFacultyCourseContent(lmsCourseId)

  switch (course.status) {
    case 'loading':
      return (
        <TeachingCourseState heading="Course">
          <LoadingState label="Loading course content…" />
        </TeachingCourseState>
      )
    case 'notFound':
      return (
        <TeachingCourseState heading="Course unavailable" srOnlyHeading={false}>
          <p className="px-4 py-6 text-sm leading-6 text-ink-muted">
            This course could not be found, or it is not one you currently teach.
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
          <ErrorState
            title="Couldn’t load course content"
            message="Check your connection and try again."
            onRetry={course.retry}
          />
        </TeachingCourseState>
      )
    case 'loaded':
      return (
        <>
          <title>{`${course.data.courseName} · Teaching · Stacc`}</title>
          <Breadcrumb parent={TEACHING} current={course.data.courseName} />
          <CourseHeader course={course.data} />
          <CourseContentEditor course={course.data} reload={course.refresh} />
        </>
      )
  }
}

/**
 * One course the signed-in faculty member teaches, at /teaching/:lmsCourseId, where they manage its
 * modules and topics. The backend checks access from official teaching assignments on every request.
 * Accounts without the faculty role see an explanation instead, and no request is made.
 */
export function TeachingCoursePage() {
  const { account } = useAuth()
  const { lmsCourseId } = useParams()
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])

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

  return <FacultyCourse lmsCourseId={parseCourseId(lmsCourseId)} />
}
