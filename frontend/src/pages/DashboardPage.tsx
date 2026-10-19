import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole, roleLabel } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { AdminDashboard } from '../dashboard/AdminDashboard'
import { FacultyDashboard } from '../dashboard/FacultyDashboard'
import { StudentDashboard } from '../dashboard/StudentDashboard'

type PreviewState = 'empty' | 'loading' | 'error'

const previewOptions: { value: PreviewState; label: string }[] = [
  { value: 'empty', label: 'Empty' },
  { value: 'loading', label: 'Loading' },
  { value: 'error', label: 'Error' },
]

const listFormat = new Intl.ListFormat('en', {
  style: 'long',
  type: 'conjunction',
})

// One part per role, always in this order, whatever order the account's roles come in.
const PARTS = [
  {
    role: 'STUDENT',
    title: 'Student Dashboard',
    summary: 'An overview of your courses and academic activity.',
    Part: StudentDashboard,
  },
  { role: 'FACULTY', title: 'Faculty Dashboard', summary: 'An overview of your teaching activity.', Part: FacultyDashboard },
  {
    role: 'ADMIN',
    title: 'Admin Dashboard',
    summary: 'An overview of academic setup and enrollment operations.',
    Part: AdminDashboard,
  },
] as const

/**
 * The home page. Its content follows every role the account holds, never one picked from the list: STUDENT gets the
 * student dashboard, FACULTY the faculty dashboard, and ADMIN the admin dashboard. An account with one of them gets
 * that dashboard as the page; an account with several gets them one under the other, in the fixed order Student,
 * Faculty, Admin. There is no role switcher. Other accounts keep the general welcome and send no dashboard request.
 * The role check is for the interface only: the backend checks every request.
 */
export function DashboardPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const parts = PARTS.filter((part) => hasAnyRole(roles, [part.role]))

  if (parts.length === 0) {
    return <GeneralDashboard />
  }
  if (parts.length === 1) {
    const { title, summary, Part } = parts[0]
    return (
      <>
        <title>Dashboard · Stacc</title>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">{summary}</p>
        <div className="mt-6">
          <Part />
        </div>
      </>
    )
  }
  return (
    <>
      <title>Dashboard · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Dashboard</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">An overview for each of your roles.</p>
      {parts.map(({ role, title, summary, Part }, index) => (
        <section
          key={role}
          aria-labelledby={`${role.toLowerCase()}-dashboard-heading`}
          className={index === 0 ? 'mt-6' : 'mt-10 border-t border-line pt-6'}
        >
          <h2 id={`${role.toLowerCase()}-dashboard-heading`} className="text-xl font-semibold tracking-tight text-ink">
            {title}
          </h2>
          <p className="mt-1 text-sm leading-6 text-ink-muted">{summary}</p>
          <div className="mt-4">
            <Part level={3} />
          </div>
        </section>
      ))}
    </>
  )
}

function GeneralDashboard() {
  const [previewState, setPreviewState] = useState<PreviewState>('empty')
  const { account } = useAuth()
  const roles = account?.roles ?? []

  return (
    <>
      <title>Dashboard · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">
        Dashboard
      </h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        Welcome to Stacc. Your college services will appear here.
      </p>
      <p className="mt-1 text-sm leading-6 text-ink-muted">
        {roles.length > 0 ? (
          <>
            Signed in as{' '}
            <span className="font-medium text-ink">
              {listFormat.format(roles.map(roleLabel))}
            </span>
            .
          </>
        ) : (
          'Your account has no Stacc role yet, so only general items are shown. Contact the college administration if this is unexpected.'
        )}
      </p>

      {/* Temporary preview of the shared page states until real dashboard content exists. */}
      <section
        aria-labelledby="page-states-heading"
        className="mt-8 rounded-panel border border-line bg-surface"
      >
        <div className="flex flex-col gap-3 border-b border-line px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
          <div>
            <h2
              id="page-states-heading"
              className="text-sm font-semibold text-ink"
            >
              Page states
            </h2>
            <p className="text-xs leading-5 text-ink-muted">
              Temporary preview of the shared states for future pages.
            </p>
          </div>
          <div
            role="group"
            aria-label="Preview a page state"
            className="flex rounded-control border border-line-strong"
          >
            {previewOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={previewState === option.value}
                onClick={() => setPreviewState(option.value)}
                className={`h-11 flex-1 border-l border-line-strong px-3 text-sm font-medium first:rounded-l-[3px] first:border-l-0 last:rounded-r-[3px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:h-9 sm:flex-none ${
                  previewState === option.value
                    ? 'bg-surface-muted text-ink underline underline-offset-4'
                    : 'text-ink-muted hover:bg-surface-muted hover:text-ink'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {previewState === 'empty' && (
          <EmptyState
            title="No items yet"
            description="Information will appear here when it becomes available."
          />
        )}
        {previewState === 'loading' && <LoadingState />}
        {previewState === 'error' && (
          <ErrorState onRetry={() => setPreviewState('loading')} />
        )}
      </section>
    </>
  )
}
