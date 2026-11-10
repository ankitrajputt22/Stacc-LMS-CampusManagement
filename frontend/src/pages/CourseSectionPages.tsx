import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { CourseAnnouncements } from '../courses/CourseAnnouncements'
import { CourseAssignments } from '../courses/CourseAssignments'
import { CourseContent } from '../courses/CourseContent'
import { CourseProgressCard } from '../courses/CourseProgressCard'
import { parseCourseId } from '../courses/courseRoute'
import { useCourseAnnouncements } from '../courses/useCourseAnnouncements'
import { useCourseAssignments } from '../courses/useCourseAssignments'
import { useLmsCourse } from '../courses/useLmsCourse'
import { useLmsCourseContent } from '../courses/useLmsCourseContent'
import type { LmsCourseDetail } from '../types/lms'
import { Badge, Card, DataTable, PageHeader } from '../ui'
import { textLinkClasses } from '../ui/styles'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

/**
 * One section of a course for the signed-in student, such as Modules: the course is loaded (from the shared cache) and
 * its states shown the same way on every section page, then the section's title and its own part.
 */
export function StudentCourseSection({
  title,
  parent,
  children,
}: {
  title: string
  /** The course section a page sits in, such as Quizzes, as a path under the course and its name. */
  parent?: { path: string; label: string }
  children: (course: LmsCourseDetail) => ReactNode
}) {
  const { account } = useAuth()
  const lmsCourseId = parseCourseId(useParams().lmsCourseId)
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const course = useLmsCourse(isStudent ? lmsCourseId : null)

  const header = (courseName?: string) => (
    <PageHeader
      title={title}
      breadcrumb={
        <Breadcrumb
          parent={
            lmsCourseId === null
              ? COURSES
              : [
                  COURSES,
                  { to: `/courses/${lmsCourseId}`, label: courseName ?? 'Course' },
                  ...(parent ? [{ to: `/courses/${lmsCourseId}/${parent.path}`, label: parent.label }] : []),
                ]
          }
          current={title}
        />
      }
    />
  )
  if (!isStudent || course.status !== 'loaded') {
    return (
      <>
        <title>{`${title} · Stacc`}</title>
        {header()}
        <StatePanel>
          {!isStudent && <AccessDeniedState title="Courses are for student accounts." message={ASK_THE_COLLEGE} />}
          {isStudent && course.status === 'loading' && <LoadingState label="Loading course…" />}
          {isStudent && course.status === 'failed' && (
            <ErrorState title="Couldn’t load this course" message="Check your connection and try again." onRetry={course.retry} />
          )}
          {isStudent && course.status === 'denied' && <AccessDeniedState title="You don’t have access to this course." message={ASK_THE_COLLEGE} />}
          {isStudent && course.status === 'notFound' && (
            <EmptyState title="Course unavailable" description="This LMS course could not be found or is not available to your account." />
          )}
        </StatePanel>
      </>
    )
  }
  return (
    <>
      <title>{`${title} · ${course.data.courseName} · Stacc`}</title>
      {header(course.data.courseName)}
      {children(course.data)}
    </>
  )
}

function Modules({ course }: { course: LmsCourseDetail }) {
  const content = useLmsCourseContent(course.lmsCourseId)
  return (
    <>
      <CourseProgressCard lmsCourseId={course.lmsCourseId} />
      <CourseContent lmsCourseId={course.lmsCourseId} content={content} />
    </>
  )
}

/** /courses/:lmsCourseId/modules: the published modules and their pages, with the student's own progress. */
export function CourseModulesPage() {
  return <StudentCourseSection title="Modules">{(course) => <Modules course={course} />}</StudentCourseSection>
}

type PageRow = { id: number; title: string; moduleTitle: string; locked: boolean; required: boolean; completed: boolean }

function Pages({ course }: { course: LmsCourseDetail }) {
  const content = useLmsCourseContent(course.lmsCourseId)
  if (content.status !== 'loaded') {
    return (
      <StatePanel>
        {content.status === 'loading' ? (
          <LoadingState label="Loading pages…" />
        ) : (
          <ErrorState title="Couldn’t load the pages" message="Check your connection and try again." onRetry={content.retry} />
        )}
      </StatePanel>
    )
  }
  const rows: PageRow[] = content.data.modules.flatMap((module) =>
    module.topics.map((topic) => ({
      id: topic.id,
      title: topic.title,
      moduleTitle: module.title,
      locked: module.locked,
      required: topic.required,
      completed: topic.completed,
    })),
  )
  return (
    <Card padding="none">
      <DataTable<PageRow>
        caption="Course pages"
        rows={rows}
        rowKey={(row) => row.id}
        empty={<EmptyState title="No pages published yet." />}
        columns={[
          {
            key: 'page',
            header: 'Page',
            primary: true,
            cell: (row) =>
              row.locked ? (
                <span className="text-ink-muted">{row.title}</span>
              ) : (
                <Link to={`/courses/${course.lmsCourseId}/pages/${row.id}`} className={textLinkClasses}>
                  {row.title}
                </Link>
              ),
          },
          { key: 'module', header: 'Module', cell: (row) => row.moduleTitle },
          {
            key: 'state',
            header: 'Your progress',
            cell: (row) =>
              row.locked ? (
                <Badge tone="neutral">Locked</Badge>
              ) : row.completed ? (
                <Badge tone="success">Completed</Badge>
              ) : row.required ? (
                <Badge tone="info">Required</Badge>
              ) : (
                <span className="text-ink-muted">Optional</span>
              ),
          },
        ]}
      />
    </Card>
  )
}

/** /courses/:lmsCourseId/pages: every published page of the course in course order, with its module and progress. */
export function CoursePagesIndexPage() {
  return <StudentCourseSection title="Pages">{(course) => <Pages course={course} />}</StudentCourseSection>
}

function Assignments({ course }: { course: LmsCourseDetail }) {
  const assignments = useCourseAssignments(course.lmsCourseId)
  return <CourseAssignments lmsCourseId={course.lmsCourseId} assignments={assignments} standalone />
}

/** /courses/:lmsCourseId/assignments: the course's published assignments. */
export function CourseAssignmentsPage() {
  return <StudentCourseSection title="Assignments">{(course) => <Assignments course={course} />}</StudentCourseSection>
}

function Announcements({ course }: { course: LmsCourseDetail }) {
  const announcements = useCourseAnnouncements(course.lmsCourseId)
  return <CourseAnnouncements announcements={announcements} lmsCourseId={course.lmsCourseId} standalone />
}

/** /courses/:lmsCourseId/announcements: the course's published announcements, newest first. */
export function CourseAnnouncementsPage() {
  return <StudentCourseSection title="Announcements">{(course) => <Announcements course={course} />}</StudentCourseSection>
}
