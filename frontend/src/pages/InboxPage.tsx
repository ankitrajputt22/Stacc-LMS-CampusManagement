import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { getHttpStatus } from '../api/apiError'
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  markNotificationUnread,
} from '../api/notificationsApi'
import { useApiRequest } from '../api/useApiRequest'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { ActionButton } from '../components/ActionButton'
import { primaryLinkClasses } from '../components/actionStyles'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { formatDateTime } from '../courses/dates'
import { useUnreadCount } from '../notifications/unreadCount'
import type { NotificationInboxItem, NotificationType } from '../types/notifications'

const PAGE_SIZE = 20
const pagerClasses = 'aria-disabled:cursor-not-allowed aria-disabled:text-ink-muted/60 aria-disabled:hover:bg-surface'
const INBOX_DENIED = 'You don’t have access to the Inbox.'
const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

const ACTION_LABELS: Record<NotificationType, string> = {
  ASSIGNMENT_PUBLISHED: 'View assignment',
  ASSIGNMENT_GRADED: 'View assignment',
  QUIZ_PUBLISHED: 'View quiz',
  ANNOUNCEMENT_PUBLISHED: 'View announcements',
  ACADEMIC_CALENDAR_PUBLISHED: 'View calendar',
  ACADEMIC_CALENDAR_CANCELLED: 'View calendar',
  ATTENDANCE_UPDATED: 'View attendance',
  RESULT_PUBLISHED: 'View results',
  RESULT_UPDATED: 'View results',
  HELPDESK_REPLY: 'View ticket',
  HELPDESK_STATUS_CHANGED: 'View ticket',
  HELPDESK_ASSIGNED: 'View ticket',
  APPROVAL_ACTION_REQUIRED: 'View request',
  APPROVAL_APPROVED: 'View request',
  APPROVAL_REJECTED: 'View request',
  APPROVAL_CANCELLED: 'View request',
  HOSTEL_ALLOCATED: 'View hostel',
  HOSTEL_VACATED: 'View hostel',
  HOSTEL_EMERGENCY: 'View hostel',
  CAMPUS_NOTICE_PUBLISHED: 'View campus notices',
  CAMPUS_SERVICE_READY: 'View request',
  CAMPUS_SERVICE_COMPLETED: 'View request',
}

function isInternalPath(path: string | null): path is string {
  return path !== null && /^\/(?![/\\])/.test(path) && !/[\s\\]/.test(path)
}

function refusalText(status: number | null): string {
  return status === 403
    ? `${INBOX_DENIED} If your access changed recently, sign out and sign in again.`
    : status === 404
      ? 'That notification is no longer available.'
      : 'Couldn’t update the notification. Check your connection and try again.'
}

type RowProps = {
  item: NotificationInboxItem
  busy: boolean
  onToggleRead: () => void
  onOpen: () => void
}

function iconFor(type: string): NavIconName {
  if (type.startsWith('ASSIGNMENT') || type.startsWith('QUIZ')) return 'assignments'
  if (type.startsWith('HELPDESK')) return 'support'
  if (type.startsWith('ACADEMIC_CALENDAR')) return 'calendar'
  if (type.startsWith('ATTENDANCE')) return 'attendance'
  if (type.startsWith('RESULT')) return 'exams'
  if (type.startsWith('APPROVAL')) return 'todo'
  if (type.startsWith('HOSTEL')) return 'hostel'
  if (type.startsWith('CAMPUS') || type.startsWith('ANNOUNCEMENT')) return 'campus'
  return 'inbox'
}

function NotificationRow({ item, busy, onToggleRead, onOpen }: RowProps) {
  const unread = item.readAt === null
  return (
    <li className={`grid gap-3 px-4 py-4 sm:px-5 md:grid-cols-[1rem_3rem_minmax(0,1fr)_auto_auto] md:items-center ${unread ? 'bg-sky-50/60' : ''}`}>
      <span aria-hidden="true" className={`hidden size-2.5 rounded-full md:block ${unread ? 'bg-stacc-primary' : ''}`} />
      <span aria-hidden="true" className="hidden size-12 items-center justify-center rounded-panel bg-surface-muted text-ink-muted md:flex">
        <NavIcon name={iconFor(item.type)} className="size-6" />
      </span>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <h2 className={`text-base break-words text-ink ${unread ? 'font-bold' : 'font-semibold'}`}>{item.title}</h2>
          <span
            className={`inline-block shrink-0 rounded-sm px-1.5 py-0.5 text-xs font-semibold ${
              unread ? 'bg-sky-100 text-portal-navy' : 'bg-success-soft text-ink'
            }`}
          >
            {unread ? 'Unread' : 'Read'}
          </span>
        </div>
        <p className="mt-1 text-[0.95rem] leading-6 break-words whitespace-pre-line text-ink">{item.message}</p>
        <p className="mt-0.5 text-sm text-ink-muted">
          <time dateTime={item.createdAt}>{formatDateTime(item.createdAt)}</time>
        </p>
      </div>
      <span>
        {isInternalPath(item.actionPath) && (
          <Link to={item.actionPath} onClick={onOpen} className={primaryLinkClasses}>
            {ACTION_LABELS[item.type] ?? 'Open'}
            <span className="sr-only">: {item.title}</span>
          </Link>
        )}
      </span>
      <span>
        <button
          type="button"
          data-focus-key={`toggle-${item.notificationRecipientId}`}
          aria-disabled={busy || undefined}
          onClick={onToggleRead}
          className="min-h-11 rounded-control px-1 text-sm font-medium text-stacc-primary underline underline-offset-4 hover:text-stacc-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary aria-disabled:opacity-60 sm:min-h-0"
        >
          {busy ? 'Saving…' : unread ? 'Mark as read' : 'Mark as unread'}
          <span className="sr-only">: {item.title}</span>
        </button>
      </span>
    </li>
  )
}

export function InboxPage() {
  const { account } = useAuth()
  const hasInbox = hasAnyRole(account?.roles ?? [], ['STUDENT', 'FACULTY', 'ADMIN', 'MAINTENANCE', 'WARDEN', 'LIBRARIAN'])
  const { unreadCount, refresh: refreshCount } = useUnreadCount()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [page, setPage] = useState(0)
  const [changed, setChanged] = useState<Record<number, NotificationInboxItem>>({})
  const [busy, setBusy] = useState<number | 'all' | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')

  const load = useMemo(
    () => (hasInbox ? (signal: AbortSignal) => getNotifications({ page, size: PAGE_SIZE, unreadOnly }, signal) : null),
    [hasInbox, page, unreadOnly],
  )
  const request = useApiRequest(load)
  const data = request.status === 'loaded' ? request.data : null

  useEffect(() => {
    if (data !== null) {
      refreshCount()
    }
  }, [data, refreshCount])

  function goToPage(next: number) {
    setPage(next)
    setChanged({})
    setActionError(null)
  }

  function chooseFilter(next: boolean) {
    if (next !== unreadOnly) {
      setUnreadOnly(next)
      setPage(0)
      setChanged({})
      setActionError(null)
    }
  }

  async function toggleRead(item: NotificationInboxItem) {
    if (busy !== null) {
      return
    }
    setBusy(item.notificationRecipientId)
    setActionError(null)
    try {
      const updated = item.readAt === null
        ? await markNotificationRead(item.notificationRecipientId)
        : await markNotificationUnread(item.notificationRecipientId)
      setChanged((current) => ({ ...current, [updated.notificationRecipientId]: updated }))
      setAnnouncement(`${item.title}: marked as ${updated.readAt === null ? 'unread' : 'read'}.`)
      refreshCount()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setActionError(refusalText(status))
      }
    } finally {
      setBusy(null)
    }
  }

  function openItem(item: NotificationInboxItem) {
    if (item.readAt === null) {
      markNotificationRead(item.notificationRecipientId).then(refreshCount, () => {})
    }
  }

  async function markAll() {
    if (busy !== null) {
      return
    }
    setBusy('all')
    setActionError(null)
    try {
      await markAllNotificationsRead()
      setAnnouncement('All notifications marked as read.')
      if (unreadOnly) {
        goToPage(0)
      } else {
        setChanged({})
      }
      request.refresh()
      refreshCount()
      headingRef.current?.focus()
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setActionError(refusalText(status))
      }
    } finally {
      setBusy(null)
    }
  }

  const filterButton = (value: boolean, label: string) => (
    <button
      type="button"
      aria-pressed={unreadOnly === value}
      onClick={() => chooseFilter(value)}
      className={`h-11 flex-1 border-l border-line-strong px-4 text-sm font-medium first:rounded-l-[3px] first:border-l-0 last:rounded-r-[3px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:h-9 sm:flex-none ${
        unreadOnly === value ? 'bg-portal-navy text-white' : 'text-ink-muted hover:bg-surface-muted hover:text-ink'
      }`}
    >
      {label}
    </button>
  )

  let content
  if (!hasInbox) {
    content = (
      <StatePanel>
        <AccessDeniedState
          title="The Inbox is for student, faculty, and admin accounts."
          message="If you think you should see it, contact the college administration."
        />
      </StatePanel>
    )
  } else if (request.status === 'loading') {
    content = (
      <StatePanel>
        <LoadingState label="Loading notifications…" />
      </StatePanel>
    )
  } else if (request.status === 'denied') {
    content = (
      <StatePanel>
        <AccessDeniedState title={INBOX_DENIED} message={SIGN_IN_AGAIN} />
      </StatePanel>
    )
  } else if (data === null) {
    content = (
      <StatePanel>
        <ErrorState
          title="Couldn’t load notifications."
          message="Check your connection and try again."
          onRetry={request.retry}
        />
      </StatePanel>
    )
  } else if (data.items.length === 0) {
    content = (
      <StatePanel>
        <EmptyState
          title={unreadOnly ? 'You’re all caught up.' : 'No notifications yet.'}
          description={unreadOnly ? 'There are no unread notifications.' : undefined}
        />
      </StatePanel>
    )
  } else {
    content = (
      <>
        <ul aria-label="Notifications" className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
          {data.items.map((original) => {
            const item = changed[original.notificationRecipientId] ?? original
            return (
              <NotificationRow
                key={item.notificationRecipientId}
                item={item}
                busy={busy === item.notificationRecipientId}
                onToggleRead={() => toggleRead(item)}
                onOpen={() => openItem(item)}
              />
            )
          })}
        </ul>
        {data.totalPages > 1 && (
          <nav aria-label="Inbox pages" className="mt-4 flex flex-wrap items-center gap-3">
            <ActionButton
              aria-disabled={page === 0 || undefined}
              className={pagerClasses}
              focusKey="page-previous"
              onClick={() => {
                if (page > 0) {
                  goToPage(page - 1)
                  focusLater('page-previous')
                }
              }}
            >
              Previous<span className="sr-only"> page</span>
            </ActionButton>
            <p className="text-sm text-ink-muted" aria-live="polite">
              Page {data.page + 1} of {data.totalPages}
            </p>
            <ActionButton
              aria-disabled={page + 1 >= data.totalPages || undefined}
              className={pagerClasses}
              focusKey="page-next"
              onClick={() => {
                if (page + 1 < data.totalPages) {
                  goToPage(page + 1)
                  focusLater('page-next')
                }
              }}
            >
              Next<span className="sr-only"> page</span>
            </ActionButton>
          </nav>
        )}
      </>
    )
  }

  return (
    <>
      <title>Inbox · Stacc</title>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 ref={headingRef} tabIndex={-1} className="text-3xl font-bold tracking-tight text-portal-navy outline-none">
            Inbox
          </h1>
          <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">Updates from your courses and academic activity.</p>
        </div>
        <Link to="/inbox/settings" className="mt-2 text-base font-medium text-stacc-primary underline underline-offset-4 hover:text-stacc-primary-hover">
          Notification settings
        </Link>
      </div>
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {hasInbox && (
        <div className="mt-6 flex flex-wrap items-center justify-between gap-3">
          <div role="group" aria-label="Show notifications" className="flex w-full rounded-control border border-line-strong sm:w-auto">
            {filterButton(false, 'All')}
            {filterButton(true, 'Unread')}
          </div>
          {unreadCount !== null && unreadCount > 0 && (
            <div className="w-full sm:w-auto">
              <Button aria-disabled={busy === 'all' || undefined} onClick={markAll}>
                {busy === 'all' ? 'Marking…' : 'Mark all as read'}
              </Button>
            </div>
          )}
        </div>
      )}

      <div role="alert">
        {actionError && (
          <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {actionError}
          </p>
        )}
      </div>

      <div ref={contentRef} className="mt-4">
        {content}
      </div>
    </>
  )
}
