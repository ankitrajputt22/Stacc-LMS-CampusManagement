import { type ReactNode, useEffect, useRef, useState } from 'react'
import type { ApiRequestState } from '../api/useApiRequest'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { type Column, MasterList } from './MasterList'

export const ADMIN_DENIED = 'You don’t have access to academic administration.'
export const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

type Request<T> = ApiRequestState<T[]> & { retry: () => void; refresh: () => void }

type MasterSectionProps<T> = {
  /** Plural and singular words, such as "Departments" and "department". */
  title: string
  singular: string
  intro: string
  request: Request<T>
  columns: Column<T>[]
  rowKey: (row: T) => number
  rowName: (row: T) => string
  /** When adding needs other masters first, such as departments for a program: why it cannot start yet. */
  blockedReason?: string | null
  /** The form for a new record. It calls `done` with an announcement once saved. */
  renderCreate: (done: (announcement: string) => void, cancel: () => void) => ReactNode
  renderEdit: (row: T, done: (announcement: string) => void, cancel: () => void) => ReactNode
}

type Open = { type: 'create' } | { type: 'edit'; key: number } | null

/**
 * One academic master: its heading, an Add button, one contained form at a time for adding or editing, and the list
 * with its loading, empty, error, and no-access states. After a save the list reloads quietly without reloading the
 * page, the form closes, and the change is announced. A 403 never signs the user out.
 */
export function MasterSection<T>({
  title,
  singular,
  intro,
  request,
  columns,
  rowKey,
  rowName,
  blockedReason,
  renderCreate,
  renderEdit,
}: MasterSectionProps<T>) {
  const sectionRef = useRef<HTMLElement>(null)
  const [open, setOpen] = useState<Open>(null)
  const [announcement, setAnnouncement] = useState('')
  const [pendingFocus, setPendingFocus] = useState<string | null>(null)
  const rows = request.status === 'loaded' ? request.data : null
  const editing = open?.type === 'edit' && rows ? rows.find((row) => rowKey(row) === open.key) ?? null : null

  // After a form closes, focus returns to the button that opened it, or to Add; whichever is visible on this screen.
  useEffect(() => {
    if (pendingFocus === null) {
      return
    }
    const target = [...(sectionRef.current?.querySelectorAll<HTMLElement>(`[data-focus-key^="${pendingFocus}"]`) ?? [])].find(
      (element) => element.offsetParent !== null,
    )
    if (target) {
      target.focus()
      setPendingFocus(null)
    }
  }, [pendingFocus, rows])

  function close(focusKey: string) {
    setOpen(null)
    setPendingFocus(focusKey)
  }

  function done(message: string, focusKey: string) {
    setAnnouncement(message)
    request.refresh()
    close(focusKey)
  }

  const add = (
    <Button
      variant="secondary"
      data-focus-key="add"
      aria-disabled={blockedReason ? true : undefined}
      aria-describedby={blockedReason ? 'master-blocked' : undefined}
      onClick={() => {
        if (!blockedReason) {
          setAnnouncement('')
          setOpen({ type: 'create' })
        }
      }}
    >
      Add {singular}
    </Button>
  )

  return (
    <section ref={sectionRef} aria-labelledby="master-heading" className="mt-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="master-heading" className="text-lg font-semibold text-ink">
          {title}
        </h2>
        {rows !== null && rows.length > 0 && open?.type !== 'create' && <div className="w-full sm:w-auto">{add}</div>}
      </div>
      <p className="mt-1 text-sm leading-6 text-ink-muted">{intro}</p>
      {rows !== null && blockedReason && (
        <p id="master-blocked" className="mt-1 text-sm text-ink-muted">
          {blockedReason}
        </p>
      )}
      <p role="status" className="sr-only">
        {announcement}
      </p>

      {open?.type === 'create' && (
        <div className="mt-3">{renderCreate((message) => done(message, 'add'), () => close('add'))}</div>
      )}
      {editing !== null && (
        <div className="mt-3">
          {renderEdit(
            editing,
            (message) => done(message, `edit-${rowKey(editing)}`),
            () => close(`edit-${rowKey(editing)}`),
          )}
        </div>
      )}

      <div className="mt-4">
        {request.status === 'loading' && (
          <StatePanel>
            <LoadingState label={`Loading ${title.toLowerCase()}…`} />
          </StatePanel>
        )}
        {request.status === 'denied' && (
          <StatePanel>
            <AccessDeniedState title={ADMIN_DENIED} message={SIGN_IN_AGAIN} />
          </StatePanel>
        )}
        {(request.status === 'failed' || request.status === 'notFound') && (
          <StatePanel>
            <ErrorState
              title={`Couldn’t load ${title.toLowerCase()}.`}
              message="Check your connection and try again."
              onRetry={request.retry}
            />
          </StatePanel>
        )}
        {rows !== null && rows.length === 0 && open?.type !== 'create' && (
          <StatePanel>
            <EmptyState title={`No ${title.toLowerCase()} yet.`} action={add} />
          </StatePanel>
        )}
        {rows !== null && rows.length > 0 && (
          <MasterList
            label={title}
            rows={rows}
            columns={columns}
            rowKey={rowKey}
            rowName={rowName}
            onEdit={(row) => {
              setAnnouncement('')
              setOpen({ type: 'edit', key: rowKey(row) })
            }}
          />
        )}
      </div>
    </section>
  )
}
