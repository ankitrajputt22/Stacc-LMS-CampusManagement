import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { getLmsCoursePage } from '../api/lmsContentApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { parseCourseId, parseRouteId } from '../courses/courseRoute'
import { PageBody } from '../courses/PageBody'
import { PageCompletion } from '../courses/PageCompletion'
import { Alert, PageHeader } from '../ui'
import { textLinkClasses } from '../ui/styles'

const COURSES = { to: '/courses', label: 'Courses' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function crumbs(lmsCourseId: number | null) {
  return lmsCourseId === null ? COURSES : [COURSES, { to: `/courses/${lmsCourseId}`, label: 'Course' }]
}

function PageState({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <>
      <title>Page · Stacc</title>
      <Breadcrumb parent={crumbs(lmsCourseId)} />
      <h1 className="sr-only">Page</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

/**
 * /courses/:lmsCourseId/pages/:pageId: one published page of a course, with its study materials and the pages before
 * and after it. The backend checks the student's access to the course and the page on every request; a page that is
 * missing, unpublished, or in another course shows the same "Page unavailable".
 */
export function LearningPage() {
  const { account } = useAuth()
  const params = useParams()
  const lmsCourseId = parseCourseId(params.lmsCourseId)
  const pageId = parseRouteId(params.pageId)
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const page = useApiQuery(
    isStudent && lmsCourseId !== null && pageId !== null ? queryKeys.lms.coursePage(lmsCourseId, pageId) : null,
    (signal) => getLmsCoursePage(lmsCourseId ?? 0, pageId ?? 0, signal),
  )

  if (!isStudent) {
    return (
      <PageState lmsCourseId={lmsCourseId}>
        <AccessDeniedState title="Course pages are for student accounts." message={ASK_THE_COLLEGE} />
      </PageState>
    )
  }

  switch (page.status) {
    case 'loading':
      return (
        <PageState lmsCourseId={lmsCourseId}>
          <LoadingState label="Loading page…" />
        </PageState>
      )
    case 'failed':
      return (
        <PageState lmsCourseId={lmsCourseId}>
          <ErrorState title="Couldn’t load this page" message="Check your connection and try again." onRetry={page.retry} />
        </PageState>
      )
    case 'denied':
      return (
        <PageState lmsCourseId={lmsCourseId}>
          <AccessDeniedState title="You don’t have access to course pages." message={ASK_THE_COLLEGE} />
        </PageState>
      )
    case 'notFound':
      return (
        <>
          <title>Page unavailable · Stacc</title>
          <Breadcrumb parent={crumbs(lmsCourseId)} />
          <h1 className="mt-4 text-3xl font-bold tracking-tight text-portal-navy">Page unavailable</h1>
          <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
            This page could not be found or is not available to your account.
          </p>
          <Link to={lmsCourseId === null ? COURSES.to : `/courses/${lmsCourseId}`} className={`mt-4 inline-block text-sm ${textLinkClasses}`}>
            {lmsCourseId === null ? 'Back to Courses' : 'Back to Course'}
          </Link>
        </>
      )
  }

  const data = page.data
  return (
    <div className="max-w-3xl">
      <title>{`${data.title} · Stacc`}</title>
      <PageHeader
        breadcrumb={<Breadcrumb parent={crumbs(lmsCourseId)} current={data.title} />}
        eyebrow={data.moduleTitle}
        title={data.title}
      />
      {data.locked ? (
        <Alert tone="info" title="This page is locked">
          Complete the required pages of “{data.waitingFor}” first. Then this module opens.
        </Alert>
      ) : (
        <>
          <PageBody
            content={data.content}
            contentFormat={data.contentFormat}
            materials={data.materials}
            previous={null}
            next={null}
            pathTo={(id) => `/courses/${lmsCourseId}/pages/${id}`}
          />
          {lmsCourseId !== null && (
            <PageCompletion lmsCourseId={lmsCourseId} pageId={data.id} required={data.required} completed={data.completed} />
          )}
        </>
      )}
      <PageBody
        content={null}
        contentFormat="PLAIN"
        materials={[]}
        previous={data.previous}
        next={data.next}
        pathTo={(id) => `/courses/${lmsCourseId}/pages/${id}`}
        neighboursOnly
      />
    </div>
  )
}
