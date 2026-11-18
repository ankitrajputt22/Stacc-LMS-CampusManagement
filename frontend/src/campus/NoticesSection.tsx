import { useMemo, useRef, useState } from 'react'
import { getCampusNotices } from '../api/campusApi'
import { useApiRequest } from '../api/useApiRequest'
import { SIGN_IN_AGAIN } from '../approvals/approvalText'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { formatDateTime } from '../courses/dates'
import { Pager } from '../support/Pager'

const PAGE_SIZE = 20

export function NoticesSection() {
  const [page, setPage] = useState(0)
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)
  const load = useMemo(() => (signal: AbortSignal) => getCampusNotices({ page, size: PAGE_SIZE }, signal), [page])
  const notices = useApiRequest(load)

  let content
  if (notices.status === 'loading') {
    content = <StatePanel><LoadingState label="Loading campus notices…" /></StatePanel>
  } else if (notices.status === 'denied') {
    content = <StatePanel><AccessDeniedState title="You don’t have access to campus notices." message={SIGN_IN_AGAIN} /></StatePanel>
  } else if (notices.status !== 'loaded') {
    content = (
      <StatePanel>
        <ErrorState title="Couldn’t load campus notices." message="Check your connection and try again." onRetry={notices.retry} />
      </StatePanel>
    )
  } else if (notices.data.items.length === 0) {
    content = <StatePanel><EmptyState title="No campus notices available." /></StatePanel>
  } else {
    content = (
      <>
        <ul aria-label="Campus notices" className="space-y-3">
          {notices.data.items.map((notice) => (
            <li key={notice.noticeId}>
              <article aria-labelledby={`campus-notice-${notice.noticeId}`} className="rounded-panel border border-line bg-surface p-4 sm:p-5">
                <h3 id={`campus-notice-${notice.noticeId}`} className="text-lg font-bold break-words text-portal-navy">
                  {notice.title}
                </h3>
                <p className="mt-0.5 text-sm text-stacc-primary">
                  Published <time dateTime={notice.publishedAt}>{formatDateTime(notice.publishedAt)}</time>
                </p>
                <p className="mt-2 text-[0.95rem] leading-6 break-words whitespace-pre-wrap text-ink">{notice.message}</p>
              </article>
            </li>
          ))}
        </ul>
        <Pager
          page={notices.data.page}
          totalPages={notices.data.totalPages}
          label="Campus notice pages"
          onPage={(next, key) => {
            setPage(next)
            focusLater(key)
          }}
        />
      </>
    )
  }

  return (
    <section aria-labelledby="campus-notices-heading" className="rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <h2 id="campus-notices-heading" className="text-xl font-bold text-portal-navy">
        Campus Notices
      </h2>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-16 rounded bg-portal-cta" />
      <div ref={contentRef} className="mt-4">
        {content}
      </div>
    </section>
  )
}
