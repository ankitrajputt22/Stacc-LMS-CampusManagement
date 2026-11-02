import type { ReactNode } from 'react'
import { useParams } from 'react-router'
import { getFacultyPage } from '../api/lmsContentApi'
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
import type { LmsFacultyPage } from '../types/lms'
import { Alert, ButtonLink } from '../ui'

const TEACHING = { to: '/teaching', label: 'Teaching' }
const ASK_THE_COLLEGE = 'If you think you should, contact the college administration.'

function crumbs(lmsCourseId: number | null) {
  return lmsCourseId === null ? TEACHING : [TEACHING, { to: `/teaching/${lmsCourseId}`, label: 'Course' }]
}

function PreviewState({ lmsCourseId, children }: { lmsCourseId: number | null; children: ReactNode }) {
  return (
    <>
      <title>Page preview · Stacc</title>
      <Breadcrumb parent={crumbs(lmsCourseId)} />
      <h1 className="sr-only">Page preview</h1>
      <div className="mt-4">
        <StatePanel>{children}</StatePanel>
      </div>
    </>
  )
}

/** Why students cannot see the page yet, from what the API sent, or null when they can. */
function hiddenBecause(page: LmsFacultyPage): string | null {
  const reasons = [
    page.status === 'DRAFT' && 'the page is a draft',
    page.moduleStatus === 'DRAFT' && 'its module is a draft',
    page.lmsCourseStatus === 'DRAFT' && 'the course is not published',
  ].filter((reason): reason is string => typeof reason === 'string')
  return reasons.length === 0 ? null : reasons.join(', ')
}

/**
 * /teaching/:lmsCourseId/pages/:pageId: a page as students will see it, drafts included, with whether they can see it
 * now. Editing stays in the course content editor. The backend finds the course from the stored page and checks that
 * the faculty member teaches it; anything else shows "Page unavailable".
 */
export function TeachingPagePreview() {
  const { account } = useAuth()
  const params = useParams()
  const routeCourseId = parseCourseId(params.lmsCourseId)
  const pageId = parseRouteId(params.pageId)
  const isFaculty = hasAnyRole(account?.roles ?? [], ['FACULTY'])
  const page = useApiQuery(isFaculty && pageId !== null ? queryKeys.lms.facultyPage(pageId) : null, (signal) =>
    getFacultyPage(pageId ?? 0, signal),
  )

  if (!isFaculty) {
    return (
      <PreviewState lmsCourseId={routeCourseId}>
        <AccessDeniedState title="Page previews are for faculty accounts." message={ASK_THE_COLLEGE} />
      </PreviewState>
    )
  }

  switch (page.status) {
    case 'loading':
      return (
        <PreviewState lmsCourseId={routeCourseId}>
          <LoadingState label="Loading page…" />
        </PreviewState>
      )
    case 'failed':
      return (
        <PreviewState lmsCourseId={routeCourseId}>
          <ErrorState title="Couldn’t load this page" message="Check your connection and try again." onRetry={page.retry} />
        </PreviewState>
      )
    case 'denied':
      return (
        <PreviewState lmsCourseId={routeCourseId}>
          <AccessDeniedState title="You don’t have access to this course’s pages." message={ASK_THE_COLLEGE} />
        </PreviewState>
      )
    case 'notFound':
      return (
        <>
          <title>Page unavailable · Stacc</title>
          <Breadcrumb parent={crumbs(routeCourseId)} />
          <h1 className="mt-4 text-3xl font-bold tracking-tight text-portal-navy">Page unavailable</h1>
          <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
            This page could not be found, is archived, or belongs to a course you don’t teach.
          </p>
        </>
      )
  }

  const data = page.data
  // The course comes from the stored page, so links use it rather than the address.
  const coursePath = `/teaching/${data.lmsCourseId}`
  const hidden = hiddenBecause(data)
  return (
    <>
      <title>{`Preview: ${data.title} · Stacc`}</title>
      <Breadcrumb parent={[TEACHING, { to: coursePath, label: data.courseName }]} current={data.title} />
      <header className="mt-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0">
          <p className="text-sm font-semibold tracking-[0.04em] break-words text-ink-muted">
            {data.courseCode} · {data.moduleTitle}
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-tight break-words text-portal-navy">{data.title}</h1>
        </div>
        <ButtonLink to={coursePath} variant="secondary">
          Edit in course content
        </ButtonLink>
      </header>
      <Alert tone={hidden ? 'warning' : 'success'} className="mt-4 max-w-3xl" title="Preview">
        {hidden
          ? `This is how students will see the page. They cannot see it yet: ${hidden}.`
          : 'This is how students enrolled in the course see this page.'}
      </Alert>
      <PageBody
        content={data.content}
        contentFormat={data.contentFormat}
        materials={data.materials}
        previous={data.previous}
        next={data.next}
        pathTo={(id) => `${coursePath}/pages/${id}`}
      />
    </>
  )
}
