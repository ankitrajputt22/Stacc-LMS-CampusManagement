import { useAuth } from '../auth/AuthContext'
import { hasAnyRole, roleLabel } from '../auth/roles'
import { AdminDashboard } from '../dashboard/AdminDashboard'
import { FacultyDashboard } from '../dashboard/FacultyDashboard'
import { StudentDashboard } from '../dashboard/StudentDashboard'
import { TodoList } from '../todo/TodoList'
import { PageHeader } from '../ui/PageHeader'

const listFormat = new Intl.ListFormat('en', {
  style: 'long',
  type: 'conjunction',
})

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
        <PageHeader title={title} description={summary} />
        <Part />
        <div className="mt-6">
          <TodoList limit={5} />
        </div>
      </>
    )
  }
  return (
    <>
      <title>Dashboard · Stacc</title>
      <PageHeader title="Dashboard" description="An overview for each of your roles." />
      <div className="mb-5">
        <TodoList limit={5} />
      </div>
      <div className="space-y-5">
        {parts.map(({ role, title, summary, Part }) => (
          <section
            key={role}
            aria-labelledby={`${role.toLowerCase()}-dashboard-heading`}
            className="rounded-panel border border-line bg-surface p-4 sm:p-5"
          >
            <h2 id={`${role.toLowerCase()}-dashboard-heading`} className="text-2xl font-bold text-ink">
              {title}
            </h2>
            <span aria-hidden="true" className="mt-1.5 block h-0.5 w-12 rounded bg-portal-cta" />
            <p className="mt-2 text-[0.95rem] text-ink-muted">{summary}</p>
            <div className="mt-4">
              <Part level={3} />
            </div>
          </section>
        ))}
      </div>
    </>
  )
}

function GeneralDashboard() {
  const { account } = useAuth()
  const roles = account?.roles ?? []

  return (
    <div className="pt-4">
      <title>Dashboard · Stacc</title>
      <h1 className="text-4xl font-bold tracking-tight text-ink sm:text-5xl">Welcome to Stacc.</h1>
      <p className="mt-2 text-lg text-ink sm:text-xl">Your college services will appear here.</p>
      <p className="mt-6 text-base text-ink sm:text-lg">
        {roles.length > 0 ? (
          <>
            Signed in as <span className="font-semibold">{listFormat.format(roles.map(roleLabel))}</span>.
          </>
        ) : (
          'Your account has no Stacc role yet, so only general items are shown. Contact the college administration if this is unexpected.'
        )}
      </p>
    </div>
  )
}
