import { type FormEvent, useMemo, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import {
  archiveCampusNotice,
  createCampusNotice,
  getAdminCampusNotices,
  publishCampusNotice,
  updateCampusNotice,
} from '../../api/campusApi'
import { useApiRequest } from '../../api/useApiRequest'
import { approvalTagClasses, plainTagClasses, refusalText, SIGN_IN_AGAIN } from '../../approvals/approvalText'
import { ActionButton } from '../../components/ActionButton'
import { Button } from '../../components/Button'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { AccessDeniedState } from '../../components/states/AccessDeniedState'
import { EmptyState } from '../../components/states/EmptyState'
import { ErrorState } from '../../components/states/ErrorState'
import { LoadingState } from '../../components/states/LoadingState'
import { StatePanel } from '../../components/states/StatePanel'
import { usePendingFocus } from '../../components/usePendingFocus'
import { formatDateTime } from '../../courses/dates'
import { Pager } from '../../support/Pager'
import { border, fieldClasses } from '../../teaching/formStyles'
import type { AdminCampusNotice } from '../../types/campus'
import { audienceLabel, NOTICE_MESSAGE_MAX, NOTICE_TITLE_MAX, noticeStatusLabel } from '../campusText'
import { CheckboxField } from './CheckboxField'

const PAGE_SIZE = 20

type Notify = (message: string, tone?: 'success' | 'error') => void
type Field = 'title' | 'message' | 'audience'

/** Add a draft or correct a draft or published notice: plain text and at least one role. */
function NoticeForm({ notice, onSaved, onClose }: { notice: AdminCampusNotice | null; onSaved: Notify; onClose: () => void }) {
  const prefix = notice ? `campus-notice-${notice.noticeId}` : 'campus-notice-new'
  const busyRef = useRef(false)
  const [title, setTitle] = useState(notice?.title ?? '')
  const [message, setMessage] = useState(notice?.message ?? '')
  const [student, setStudent] = useState(notice?.visibleToStudent ?? true)
  const [faculty, setFaculty] = useState(notice?.visibleToFaculty ?? true)
  const [admin, setAdmin] = useState(notice?.visibleToAdmin ?? false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<{ field: Field | null; text: string } | null>(null)

  function fail(field: Field | null, text: string, focusId?: string) {
    setError({ field, text })
    if (focusId) document.getElementById(focusId)?.focus()
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busyRef.current) return
    if (title.trim() === '') return fail('title', 'Enter a title.', `${prefix}-title`)
    if (message.trim() === '') return fail('message', 'Enter the message.', `${prefix}-message`)
    if (!student && !faculty && !admin) return fail('audience', 'Choose at least one role.', `${prefix}-student`)
    busyRef.current = true
    setBusy(true)
    setError(null)
    const content = { title: title.trim(), message: message.trim(), visibleToStudent: student, visibleToFaculty: faculty, visibleToAdmin: admin }
    try {
      if (notice) {
        await updateCampusNotice(notice.noticeId, content)
        onSaved('Notice saved.')
      } else {
        await createCampusNotice(content)
        onSaved('Draft notice added. Publish it when it is ready.')
      }
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status === 409) {
        // Archived meanwhile: the list shows it as it is now.
        onSaved(refusalText(409, getApiError(failure)?.message ?? null, 'save the notice'), 'error')
      } else if (status !== 401) {
        fail(null, refusalText(status, getApiError(failure)?.message ?? null, 'save the notice'))
      }
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label={notice ? `Edit ${notice.title}` : 'Add notice'} className="mt-3 grid gap-4 rounded-control border border-line-strong bg-surface p-3">
      <div>
        <label htmlFor={`${prefix}-title`} className="block text-sm font-medium text-ink">Title</label>
        <input
          id={`${prefix}-title`}
          type="text"
          autoComplete="off"
          maxLength={NOTICE_TITLE_MAX}
          value={title}
          aria-invalid={error?.field === 'title' || undefined}
          onChange={(event) => { setTitle(event.target.value); setError(null) }}
          className={`h-11 ${fieldClasses} ${border(error?.field === 'title')}`}
        />
      </div>
      <div>
        <label htmlFor={`${prefix}-message`} className="block text-sm font-medium text-ink">Message</label>
        <textarea
          id={`${prefix}-message`}
          rows={6}
          maxLength={NOTICE_MESSAGE_MAX}
          value={message}
          aria-invalid={error?.field === 'message' || undefined}
          aria-describedby={`${prefix}-message-hint`}
          onChange={(event) => { setMessage(event.target.value); setError(null) }}
          className={`py-2 ${fieldClasses} ${border(error?.field === 'message')}`}
        />
        <p id={`${prefix}-message-hint`} className="mt-1 text-xs text-ink-muted">Plain text.</p>
      </div>
      <fieldset>
        <legend className="text-sm font-medium text-ink">Visible to</legend>
        <div className="mt-1 flex flex-wrap gap-x-6">
          <CheckboxField id={`${prefix}-student`} label="Visible to Student" checked={student} onChange={(next) => { setStudent(next); setError(null) }} />
          <CheckboxField id={`${prefix}-faculty`} label="Visible to Faculty" checked={faculty} onChange={(next) => { setFaculty(next); setError(null) }} />
          <CheckboxField id={`${prefix}-admin`} label="Visible to Admin" checked={admin} onChange={(next) => { setAdmin(next); setError(null) }} />
        </div>
        <p className="text-xs text-ink-muted">At least one must be selected.</p>
      </fieldset>
      <div role="alert" className="empty:hidden">
        {error && <p className="rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{error.text}</p>}
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={busy || undefined}>
          {busy ? 'Saving…' : notice ? 'Save notice' : 'Add draft'}
        </Button>
        <ActionButton onClick={onClose}>Cancel</ActionButton>
      </div>
    </form>
  )
}

/** Publish or Archive, each confirmed. A 409 is shown at page level and the list is reloaded. */
function LifecycleButton({ notice, kind, onChanged }: { notice: AdminCampusNotice; kind: 'publish' | 'archive'; onChanged: Notify }) {
  const [confirming, setConfirming] = useState(false)
  const label = kind === 'publish' ? 'Publish' : 'Archive'
  if (!confirming) {
    return (
      <ActionButton onClick={() => setConfirming(true)}>
        {label}<span className="sr-only"> {notice.title}</span>
      </ActionButton>
    )
  }
  return (
    <div className="w-full">
      <StatusConfirmation
        question={kind === 'publish' ? 'Publish campus notice?' : 'Archive this campus notice?'}
        detail={
          kind === 'publish'
            ? 'It will become visible to selected roles and may generate Inbox notifications.'
            : 'It will no longer appear in the normal Campus notice feed.'
        }
        confirmLabel={kind === 'publish' ? 'Publish notice' : 'Archive notice'}
        busyLabel={kind === 'publish' ? 'Publishing…' : 'Archiving…'}
        onCancel={() => setConfirming(false)}
        onConfirm={async () => {
          try {
            await (kind === 'publish' ? publishCampusNotice(notice.noticeId) : archiveCampusNotice(notice.noticeId))
          } catch (failure) {
            if (getHttpStatus(failure) === 409) {
              setConfirming(false)
              onChanged(refusalText(409, getApiError(failure)?.message ?? null, `${kind} the notice`), 'error')
              return
            }
            throw failure
          }
          setConfirming(false)
          onChanged(kind === 'publish' ? 'Notice published.' : 'Notice archived.')
        }}
        describeRefusal={(status, message) => refusalText(status, message, `${kind} the notice`)}
      />
    </div>
  )
}

/** Every notice, newest first: drafts can be edited, published, or archived; published ones edited or archived. */
export function NoticesSection() {
  const [page, setPage] = useState(0)
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)
  const load = useMemo(() => (signal: AbortSignal) => getAdminCampusNotices({ page, size: PAGE_SIZE }, signal), [page])
  const notices = useApiRequest(load)
  const [editing, setEditing] = useState<number | 'new' | null>(null)
  const [notice, setNotice] = useState<{ text: string; tone: 'success' | 'error' } | null>(null)

  const changed: Notify = (text, tone = 'success') => {
    setEditing(null)
    setNotice({ text, tone })
    notices.refresh()
  }

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
    content = <StatePanel><EmptyState title="No campus notices available." description="Add a draft notice and publish it when it is ready." /></StatePanel>
  } else {
    content = (
      <>
        <ul aria-label="Campus notices" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
          {notices.data.items.map((item) => (
            <li key={item.noticeId} className="px-4 py-3 sm:px-5">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-sm font-semibold break-words text-ink">{item.title}</span>
                  <span className={item.status === 'PUBLISHED' ? approvalTagClasses('APPROVED') : plainTagClasses(item.status === 'ARCHIVED')}>
                    <span className="sr-only">Status: </span>
                    {noticeStatusLabel(item.status)}
                  </span>
                </div>
                <p className="mt-0.5 text-xs text-ink-muted">
                  Visible to {audienceLabel(item.visibleToStudent, item.visibleToFaculty, item.visibleToAdmin)}
                  {item.publishedAt && (
                    <>
                      {' '}· Published <time dateTime={item.publishedAt}>{formatDateTime(item.publishedAt)}</time>
                    </>
                  )}
                </p>
              </div>
              {item.status !== 'ARCHIVED' && editing !== item.noticeId && (
                <div className="mt-2 flex flex-wrap gap-2">
                  <ActionButton onClick={() => { setEditing(item.noticeId); setNotice(null) }}>
                    Edit<span className="sr-only"> {item.title}</span>
                  </ActionButton>
                  {item.status === 'DRAFT' && <LifecycleButton notice={item} kind="publish" onChanged={changed} />}
                  <LifecycleButton notice={item} kind="archive" onChanged={changed} />
                </div>
              )}
              {item.status === 'ARCHIVED' && <p className="mt-1 text-xs text-ink-muted">Archived notices are read-only.</p>}
              {editing === item.noticeId && <NoticeForm notice={item} onSaved={changed} onClose={() => setEditing(null)} />}
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
    <>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-base font-semibold text-ink">Notices</h2>
        {editing !== 'new' && notices.status === 'loaded' && (
          <ActionButton onClick={() => { setEditing('new'); setNotice(null) }}>Add notice</ActionButton>
        )}
      </div>
      <p role="status" className="mt-2 text-sm text-success empty:hidden">{notice?.tone === 'success' ? notice.text : ''}</p>
      <div role="alert" className="empty:hidden">
        {notice?.tone === 'error' && (
          <p className="mt-2 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm text-danger">{notice.text}</p>
        )}
      </div>
      {editing === 'new' && <NoticeForm notice={null} onSaved={changed} onClose={() => setEditing(null)} />}
      <div ref={contentRef} className="mt-3">
        {content}
      </div>
    </>
  )
}
