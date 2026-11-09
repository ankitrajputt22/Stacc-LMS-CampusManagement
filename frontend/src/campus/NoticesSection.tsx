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

/** The published notices for any role the account holds, newest first, each once. Title and message are plain text. */
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
                <h3 id={`campus-notice-${notice.noticeId}`} className="text-sm font-semibold break-words text-ink">
                  {notice.title}
                </h3>
                <p className="mt-0.5 text-xs text-ink-muted">
                  Published <time dateTime={notice.publishedAt}>{formatDateTime(notice.publishedAt)}</time>
                </p>
                {/* Plain text: React shows it as text, never as HTML. */}
                <p className="mt-2 text-sm leading-6 break-words whitespace-pre-wrap text-ink">{notice.message}</p>
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
    <section aria-labelledby="campus-notices-heading">
      <h2 id="campus-notices-heading" className="text-base font-semibold text-ink">
        Campus Notices
      </h2>
      <div ref={contentRef} className="mt-3">
        {content}
      </div>
    </section>
  )
}
