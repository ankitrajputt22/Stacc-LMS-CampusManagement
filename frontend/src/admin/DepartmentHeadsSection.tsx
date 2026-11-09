import {
  assignDepartmentHead,
  endDepartmentHead,
  getDepartmentHeadCandidates,
  getDepartmentHeadHistory,
  getDepartmentHeads,
} from '../api/departmentHeadsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { formatDateTime } from '../courses/dates'
import type { DepartmentHeadRow, DepartmentHeadTerm } from '../types/departmentHeads'
import { Badge, Button } from '../ui'
import { MasterForm } from './MasterForm'
import { MasterStatus } from './MasterParts'
import { MasterSection } from './MasterSection'

const DENIED = 'You don’t have access to department heads.'

/** The person's name, or the login ID before a name is recorded. */
function who(head: { name: string | null; loginId: string }): string {
  return head.name ?? head.loginId
}

function HeadCell({ head }: { head: DepartmentHeadTerm | null }) {
  if (head === null) {
    return <span className="text-ink-muted">No head</span>
  }
  return (
    <>
      {who(head)}
      <span className="block text-sm font-normal text-ink-muted">
        {head.name && `${head.loginId} · `}since {formatDateTime(head.startedAt)}
      </span>
      {!head.counts && (
        <span className="mt-1 flex flex-wrap items-center gap-2 text-sm font-normal text-ink-muted">
          <Badge tone="warning">Not counted</Badge>
          {head.notCountedReason}
        </span>
      )}
    </>
  )
}

/** Every earlier and current term of one department, the latest first. */
function HeadHistory({ departmentId }: { departmentId: number }) {
  const history = useApiQuery(queryKeys.admin.departmentHeadHistory(departmentId), (signal) =>
    getDepartmentHeadHistory(departmentId, signal),
  )
  if (history.status !== 'loaded') {
    return history.status === 'loading' ? <p className="mt-3 text-sm text-ink-muted">Loading the history…</p> : null
  }
  return (
    <section aria-label="Heads over time" className="mt-4">
      <h4 className="text-sm font-semibold text-ink">Heads over time</h4>
      {history.data.length === 0 ? (
        <p className="mt-1 text-sm text-ink-muted">Nobody has headed this department yet.</p>
      ) : (
        <ul className="mt-1 space-y-1 text-sm text-ink">
          {history.data.map((term) => (
            <li key={term.departmentHeadId}>
              {who(term)} ({term.loginId}): {formatDateTime(term.startedAt)} to{' '}
              {term.endedAt ? formatDateTime(term.endedAt) : 'now'}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/**
 * Who heads each department. Edit sets a head from the active accounts holding the Head of Department role, ending
 * the current term first; End term ends it after confirmation. Terms are kept as history and nothing is deleted. A head
 * whose account is disabled or lost the role is shown as not counted, as the backend works it out.
 */
export function DepartmentHeadsSection() {
  const request = useApiQuery(queryKeys.admin.departmentHeads(), getDepartmentHeads)
  const candidates = useApiQuery(queryKeys.admin.departmentHeadCandidates(), getDepartmentHeadCandidates)
  const options =
    candidates.status === 'loaded'
      ? candidates.data.map((candidate) => ({
          value: String(candidate.userAccountId),
          label: candidate.name ? `${candidate.name} (${candidate.loginId})` : candidate.loginId,
        }))
      : []

  return (
    <MasterSection<DepartmentHeadRow>
      title="Department Heads"
      singular="department head"
      intro="Who heads each department, with every earlier term kept. Only active accounts holding the Head of Department role can be chosen; give that role on the account’s page under Users."
      request={request}
      deniedTitle={DENIED}
      emptyTitle="No departments yet."
      rowKey={(row) => row.departmentId}
      rowName={(row) => `head of ${row.departmentCode}`}
      searchText={(row) =>
        `${row.departmentCode} ${row.departmentName} ${row.head?.loginId ?? ''} ${row.head?.name ?? ''}`
      }
      rowStatus={(row) => row.departmentStatus}
      columns={[
        {
          header: 'Department',
          primary: true,
          cell: (row) => (
            <>
              {row.departmentCode}
              <span className="block text-sm font-normal text-ink-muted">{row.departmentName}</span>
            </>
          ),
        },
        { header: 'Head', cell: (row) => <HeadCell head={row.head} /> },
        { header: 'Department status', cell: (row) => <MasterStatus status={row.departmentStatus} /> },
      ]}
      rowActions={(row, actions) => {
        const head = row.head
        return head && (
          <Button
            variant="secondary"
            size="sm"
            data-focus-key={`end-${row.departmentId}`}
            onClick={() =>
              actions.confirm(
                {
                  question: `End ${who(head)}’s term as head of ${row.departmentCode}?`,
                  detail: 'The term stays in the history. The department has no head until a new one is set.',
                  confirmLabel: 'End term',
                  busyLabel: 'Ending term…',
                  run: async () => {
                    await endDepartmentHead(row.departmentId)
                    return `${row.departmentCode} has no head now.`
                  },
                },
                `end-${row.departmentId}`,
              )
            }
          >
            End term<span className="sr-only"> of the head of {row.departmentCode}</span>
          </Button>
        )
      }}
      renderEdit={(row, done, cancel) => (
        <>
          <MasterForm
            heading={`Set the head of ${row.departmentCode}`}
            note={
              options.length === 0
                ? 'No active account holds the Head of Department role yet. Give that role on the account’s page under Users first.'
                : row.head
                  ? `Saving someone else ends ${who(row.head)}’s term; it stays in the history.`
                  : 'The chosen person heads the department from now on.'
            }
            deniedMessage={DENIED}
            fields={[
              {
                name: 'userAccountId',
                label: 'Head of department',
                kind: 'select',
                options,
                choose: 'Choose a person',
              },
            ]}
            initial={{ userAccountId: row.head?.counts ? String(row.head.userAccountId) : '' }}
            submitLabel="Save head"
            onSubmit={async (values) => {
              const saved = await assignDepartmentHead(row.departmentId, Number(values.userAccountId))
              done(`${saved.head ? who(saved.head) : 'Nobody'} heads ${row.departmentCode}.`)
            }}
            onCancel={cancel}
          />
          <HeadHistory departmentId={row.departmentId} />
        </>
      )}
    />
  )
}
