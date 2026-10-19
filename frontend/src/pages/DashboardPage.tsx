import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole, roleLabel } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
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

/**
 * The home page. Its content follows every role the account holds, never one picked from the list: STUDENT gets the
 * student dashboard, FACULTY the faculty dashboard, and an account with both gets both, one under the other in the
 * fixed order Student, Faculty. Other accounts keep the general welcome until their own dashboards exist, and send no
 * dashboard request. The role check is for the interface only: the backend checks every request.
 */
export function DashboardPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const isStudent = hasAnyRole(roles, ['STUDENT'])
  const isFaculty = hasAnyRole(roles, ['FACULTY'])

  if (isStudent && isFaculty) {
    return (
      <>
        <title>Dashboard · Stacc</title>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Dashboard</h1>
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          An overview of your courses, academic activity, and teaching.
        </p>
        <section aria-labelledby="student-dashboard-heading" className="mt-6">
          <h2 id="student-dashboard-heading" className="text-xl font-semibold tracking-tight text-ink">
            Student Dashboard
          </h2>
          <div className="mt-4">
            <StudentDashboard level={3} />
          </div>
        </section>
        <section aria-labelledby="faculty-dashboard-heading" className="mt-10 border-t border-line pt-6">
          <h2 id="faculty-dashboard-heading" className="text-xl font-semibold tracking-tight text-ink">
            Faculty Dashboard
          </h2>
          <div className="mt-4">
            <FacultyDashboard level={3} />
          </div>
        </section>
      </>
    )
  }
  if (isStudent) {
    return (
      <>
        <title>Dashboard · Stacc</title>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Student Dashboard</h1>
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
          An overview of your courses and academic activity.
        </p>
        <div className="mt-6">
          <StudentDashboard />
        </div>
      </>
    )
  }
  if (isFaculty) {
    return (
      <>
        <title>Dashboard · Stacc</title>
        <h1 className="text-2xl font-semibold tracking-tight text-ink">Faculty Dashboard</h1>
        <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">An overview of your teaching activity.</p>
        <div className="mt-6">
          <FacultyDashboard />
        </div>
      </>
    )
  }
  return <GeneralDashboard />
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
