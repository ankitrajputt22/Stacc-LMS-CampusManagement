import { Outlet, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { parseCourseId } from './courseRoute'
import { CourseFrame, CourseNav } from './CourseNav'
import { useLmsCourse } from './useLmsCourse'

/**
 * Every student page of one course, under /courses/:lmsCourseId, with the course navigation: Home, Modules, Pages,
 * Assignments, Announcements, Files, Syllabus, and the AI Study Assistant. Each page still asks the backend for its own
 * data and shows its own states; the navigation appears once the course itself has loaded.
 */
export function CourseLayout() {
  const { account } = useAuth()
  const lmsCourseId = parseCourseId(useParams().lmsCourseId)
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const course = useLmsCourse(isStudent ? lmsCourseId : null)

  if (course.status !== 'loaded' || lmsCourseId === null) {
    return <Outlet />
  }
  const root = `/courses/${lmsCourseId}`
  return (
    <CourseFrame
      nav={
        <CourseNav
          title={course.data.courseCode}
          items={[
            { to: root, label: 'Home', end: true },
            { to: `${root}/modules`, label: 'Modules' },
            { to: `${root}/pages`, label: 'Pages' },
            { to: `${root}/assignments`, label: 'Assignments' },
            { to: `${root}/announcements`, label: 'Announcements' },
            { to: `${root}/files`, label: 'Files' },
            { to: `${root}/syllabus`, label: 'Syllabus' },
            { to: `${root}/ai`, label: 'AI Study Assistant' },
          ]}
        />
      }
    >
      <Outlet />
    </CourseFrame>
  )
}
