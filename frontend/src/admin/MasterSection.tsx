import { useQueryClient } from '@tanstack/react-query'
import { type ReactNode, useEffect, useId, useRef, useState } from 'react'
import { queryKeys } from '../api/queryKeys'
import type { ApiRequestState } from '../api/useApiRequest'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { Button, Card, type Column, DataTable, Pagination, SelectField, TextField } from '../ui'
import { statusWord } from './masterStatus'

export const ADMIN_DENIED = 'You don’t have access to academic administration.'
export const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

export const PAGE_SIZE = 20

type Request<T> = ApiRequestState<T[]> & { retry: () => void; refresh: () => void }

export type MasterColumn<T> = Omit<Column<T>, 'key'> & { key?: string }

export type ListFilter<T> = {
  label: string
  all: string
  options: { value: string; label: string }[]
  value: (row: T) => string
}

export type Confirmation = {
  question: string
  detail: string
  confirmLabel: string
  busyLabel?: string
  run: () => Promise<string>
}

export type RowActions = {
  confirm: (confirmation: Confirmation, focusKey: string) => void
}

type MasterSectionProps<T> = {
  title: string
  singular: string
  intro: string
  request: Request<T>
  columns: MasterColumn<T>[]
  rowKey: (row: T) => number
  rowName: (row: T) => string
  searchText: (row: T) => string
  clientSearch?: boolean
  rowStatus?: (row: T) => string
  filters?: ListFilter<T>[]
  blockedReason?: string | null
  renderCreate?: (done: (announcement: string) => void, cancel: () => void) => ReactNode
  renderEdit: (row: T, done: (announcement: string) => void, cancel: () => void) => ReactNode
  emptyTitle?: string
  deniedTitle?: string
  toolbar?: ReactNode
  rowActions?: (row: T, actions: RowActions) => ReactNode
  describeRefusal?: (status: number | null, apiMessage: string | null) => string
}

type Open =
  | { type: 'create' }
  | { type: 'edit'; key: number }
  | { type: 'confirm'; confirmation: Confirmation; focusKey: string }
  | null

function defaultRefusal(status: number | null, apiMessage: string | null): string {
  if (status === 403) {
    return `${ADMIN_DENIED} If your access changed recently, sign out and sign in again.`
  }
  return apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Check your connection and try again.'
}

export function MasterSection<T>({
  title,
  singular,
  intro,
  request,
  columns,
  rowKey,
  rowName,
  searchText,
  clientSearch = true,
  rowStatus,
  filters = [],
  blockedReason,
  renderCreate,
  renderEdit,
  emptyTitle,
  deniedTitle = ADMIN_DENIED,
  toolbar,
  rowActions,
  describeRefusal = defaultRefusal,
}: MasterSectionProps<T>) {
  const id = useId()
  const queryClient = useQueryClient()
  const sectionRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState<Open>(null)
  const [announcement, setAnnouncement] = useState('')
  const [pendingFocus, setPendingFocus] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState('')
  const [chosen, setChosen] = useState<Record<number, string>>({})
  const [page, setPage] = useState(1)
  const rows = request.status === 'loaded' ? request.data : null
  const editing = open?.type === 'edit' && rows ? rows.find((row) => rowKey(row) === open.key) ?? null : null

  const needle = search.trim().toLowerCase()
  const shown = (rows ?? []).filter(
    (row) =>
      (needle === '' || searchText(row).toLowerCase().includes(needle)) &&
      (status === '' || rowStatus?.(row) === status) &&
      filters.every((filter, index) => (chosen[index] ?? '') === '' || filter.value(row) === chosen[index]),
  )
  const pages = Math.max(1, Math.ceil(shown.length / PAGE_SIZE))
  const current = Math.min(page, pages)
  const pageRows = shown.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE)
  const statuses = rowStatus ? [...new Set((rows ?? []).map(rowStatus))] : []

  useEffect(() => {
    if (pendingFocus === null) {
      return
    }
    const target = [...(sectionRef.current?.querySelectorAll<HTMLElement>(`[data-focus-key="${pendingFocus}"]`) ?? [])].find(
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
    void queryClient.invalidateQueries({ queryKey: queryKeys.admin.academic })
    close(focusKey)
  }

  const add = (focusKey: string) => (
    <Button
      variant="primary"
      data-focus-key={focusKey}
      aria-disabled={blockedReason ? true : undefined}
      aria-describedby={blockedReason ? `${id}-blocked` : undefined}
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
  const emptyList = rows !== null && rows.length === 0

  const tableColumns: Column<T>[] = [
    ...columns.map((column, index) => ({ ...column, key: column.key ?? `${index}-${column.header}` })),
    {
      key: 'actions',
      header: 'Actions',
      align: 'right' as const,
      cell: (row: T) => (
        <div className="flex flex-wrap gap-2 md:justify-end">
          <Button
            variant="secondary"
            size="sm"
            data-focus-key={`edit-${rowKey(row)}`}
            onClick={() => {
              setAnnouncement('')
              setOpen({ type: 'edit', key: rowKey(row) })
            }}
          >
            Edit<span className="sr-only"> {rowName(row)}</span>
          </Button>
          {rowActions?.(row, {
            confirm: (confirmation, focusKey) => {
              setAnnouncement('')
              setOpen({ type: 'confirm', confirmation, focusKey })
            },
          })}
        </div>
      ),
    },
  ]

  return (
    <div ref={sectionRef}>
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {open?.type === 'confirm' && (
        <div className="mb-4">
          <StatusConfirmation
            question={open.confirmation.question}
            detail={open.confirmation.detail}
            confirmLabel={open.confirmation.confirmLabel}
            busyLabel={open.confirmation.busyLabel}
            onConfirm={async () => done(await open.confirmation.run(), open.focusKey)}
            onCancel={() => close(open.focusKey)}
            describeRefusal={describeRefusal}
          />
        </div>
      )}
      {open?.type === 'create' && renderCreate && (
        <div className="mb-4">{renderCreate((message) => done(message, 'add'), () => close(emptyList ? 'add-empty' : 'add'))}</div>
      )}
      {editing !== null && (
        <div className="mb-4">
          {renderEdit(
            editing,
            (message) => done(message, `edit-${rowKey(editing)}`),
            () => close(`edit-${rowKey(editing)}`),
          )}
        </div>
      )}

      <Card
        title={title}
        description={
          <>
            {intro}
            {rows !== null && blockedReason && (
              <span id={`${id}-blocked`} className="mt-1 block">
                {blockedReason}
              </span>
            )}
          </>
        }
        action={renderCreate && rows !== null && rows.length > 0 && open?.type !== 'create' ? add('add') : undefined}
        padding="none"
      >
        {(toolbar || (rows !== null && rows.length > 0)) && (
          <div className="grid gap-4 border-b border-line px-4 py-4 sm:grid-cols-2 sm:px-5 lg:grid-cols-4 xl:grid-cols-5">
            {toolbar}
            {rows !== null && rows.length > 0 && (
              <>
                {clientSearch && (
                <TextField
                  label={`Search ${title.toLowerCase()}`}
                  type="search"
                  value={search}
                  autoComplete="off"
                  onChange={(event) => {
                    setSearch(event.target.value)
                    setPage(1)
                  }}
                />
                )}
                {filters.map((filter, index) => (
                  <SelectField
                    key={filter.label}
                    label={filter.label}
                    value={chosen[index] ?? ''}
                    onChange={(event) => {
                      setChosen((current) => ({ ...current, [index]: event.target.value }))
                      setPage(1)
                    }}
                  >
                    <option value="">{filter.all}</option>
                    {filter.options.map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </SelectField>
                ))}
                {statuses.length > 1 && (
                  <SelectField
                    label="Status"
                    value={status}
                    onChange={(event) => {
                      setStatus(event.target.value)
                      setPage(1)
                    }}
                  >
                    <option value="">All statuses</option>
                    {statuses.map((value) => (
                      <option key={value} value={value}>
                        {statusWord(value)}
                      </option>
                    ))}
                  </SelectField>
                )}
              </>
            )}
          </div>
        )}

        {request.status === 'loading' && <LoadingState label={`Loading ${title.toLowerCase()}…`} />}
        {request.status === 'denied' && <AccessDeniedState title={deniedTitle} message={SIGN_IN_AGAIN} />}
        {(request.status === 'failed' || request.status === 'notFound') && (
          <ErrorState
            title={`Couldn’t load ${title.toLowerCase()}.`}
            message="Check your connection and try again."
            onRetry={request.retry}
          />
        )}
        {rows !== null && rows.length === 0 && open?.type !== 'create' && (
          <EmptyState title={emptyTitle ?? `No ${title.toLowerCase()} yet.`} action={renderCreate ? add('add-empty') : undefined} />
        )}
        {rows !== null && rows.length > 0 && (
          <>
            <DataTable
              caption={title}
              columns={tableColumns}
              rows={pageRows}
              rowKey={rowKey}
              empty={<EmptyState title={`No ${title.toLowerCase()} match.`} description="Change the search or the filters." />}
            />
            <Pagination page={current} pageSize={PAGE_SIZE} total={shown.length} onPage={setPage} noun={title.toLowerCase()} />
          </>
        )}
      </Card>
    </div>
  )
}
