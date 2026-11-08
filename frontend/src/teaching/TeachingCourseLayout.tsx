import { Outlet, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { parseCourseId } from '../courses/courseRoute'
import { CourseFrame, CourseNav } from '../courses/CourseNav'
import { useFacultyCourse } from './useFacultyCourse'

/**
 * Every faculty page of one course, under /teaching/:lmsCourseId, with the course navigation: Home (course settings and
 * the course home), Modules, Announcements, Assignments, Files, Syllabus, and AI Tools. The navigation appears once
 * the course has loaded; each page shows its own states.
 */
export function TeachingCourseLayout() {
  const { account } = useAuth()
  const lmsCourseId = parseCourseId(useParams().lmsCourseId)
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const course = useFacultyCourse(isFaculty ? lmsCourseId : null)

  if (course.status !== 'loaded' || lmsCourseId === null) {
    return <Outlet />
  }
  const root = `/teaching/${lmsCourseId}`
  return (
    <CourseFrame
      nav={
        <CourseNav
          title={course.data.courseCode}
          items={[
            { to: root, label: 'Home', end: true },
            { to: `${root}/modules`, label: 'Modules' },
            { to: `${root}/announcements`, label: 'Announcements' },
            { to: `${root}/assignments`, label: 'Assignments' },
            { to: `${root}/files`, label: 'Files' },
            { to: `${root}/syllabus`, label: 'Syllabus' },
            { to: `${root}/ai`, label: 'AI Tools' },
          ]}
        />
      }
    >
      <Outlet />
    </CourseFrame>
  )
}
