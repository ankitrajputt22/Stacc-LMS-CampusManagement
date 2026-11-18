import { Link } from 'react-router'
import { getMyDegreeProgress, getMyExams } from '../api/erpApi'
import { getMyFees } from '../api/feesApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { formatCredits } from '../erp/erpData'
import { hhmm } from '../erp/examFormat'
import { rupees } from '../erp/money'
import { formatDay } from '../erp/requestWords'
import { ButtonLink, Card } from '../ui'

export function ErpPreview({ level }: { level: 2 | 3 }) {
  const fees = useApiQuery(queryKeys.erp.myFees(), getMyFees)
  const exams = useApiQuery(queryKeys.erp.myExams(), getMyExams)
  const ledger = fees.status === 'loaded' ? fees.data : null
  const today = new Date().toISOString().slice(0, 10)
  const next = exams.status === 'loaded' ? exams.data.find((exam) => exam.examDate >= today) ?? null : null
  return (
    <>
      {ledger && ledger.total > 0 && (
        <Card title="Fee Due" level={level}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className={`text-3xl font-bold ${ledger.due > 0 ? 'text-portal-cta' : 'text-success'}`}>{rupees(Math.max(0, ledger.due))}</p>
              {ledger.dueDate && ledger.due > 0 && <p className="text-sm text-ink-muted">{`${ledger.overdue ? 'Was due' : 'Due on'} ${formatDay(ledger.dueDate)}`}</p>}
              {ledger.due <= 0 && <p className="text-sm text-ink-muted">Paid in full</p>}
            </div>
            <ButtonLink to="/erp/fees" size="sm">View fees</ButtonLink>
          </div>
        </Card>
      )}
      {next && (
        <Card title="Next Exam" level={level}>
          <Link to="/erp/exams" className="flex items-center gap-3 rounded-md focus-visible:outline-2 focus-visible:outline-stacc-primary">
            <NavIcon name="calendar" className="size-6 text-portal-navy" />
            <span className="min-w-0 flex-1">
              <span className="block font-semibold text-ink">{`${next.courseCode} – ${next.courseName}`}</span>
              <span className="block text-sm text-ink-muted">{`${formatDay(next.examDate)}, ${hhmm(next.startsAt)} · ${next.hall}${next.seatNumber ? ` · Seat ${next.seatNumber}` : ''}`}</span>
            </span>
            <NavIcon name="chevronRight" className="size-5 text-ink-muted" />
          </Link>
        </Card>
      )}
    </>
  )
}

const LINKS: { to: string; label: string; icon: NavIconName }[] = [
  { to: '/courses', label: 'Courses', icon: 'courses' },
  { to: '/erp/exams/results', label: 'Results', icon: 'exams' },
  { to: '/erp/fees', label: 'Fees', icon: 'fees' },
  { to: '/erp/exams', label: 'Exams', icon: 'calendar' },
  { to: '/erp/attendance', label: 'Attendance', icon: 'attendance' },
]

export function QuickLinks() {
  const progress = useApiQuery(queryKeys.erp.degreeProgress(), getMyDegreeProgress)
  const degree = progress.status === 'loaded' && progress.data.groups.length > 0 ? progress.data : null
  return (
    <nav aria-label="Quick links" className="flex flex-wrap items-center gap-x-6 gap-y-3 rounded-panel border border-line bg-surface px-5 py-4">
      <span className="font-semibold text-ink">Quick Links:</span>
      {LINKS.map((link) => (
        <Link key={link.to} to={link.to} className="flex items-center gap-2 text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary">
          <NavIcon name={link.icon} />{link.label}
        </Link>
      ))}
      {degree && (
        <Link to="/erp/academics/degree-progress" className="ml-auto text-ink-muted hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary">
          Overall Progress: <strong className="text-ink">{`${formatCredits(Number(degree.earnedCredits))} / ${formatCredits(Number(degree.requiredCredits))}`}</strong> Credits
        </Link>
      )}
    </nav>
  )
}
