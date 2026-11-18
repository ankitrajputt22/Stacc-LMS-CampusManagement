import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { getCommitteeGrievances, getGrievanceCategories, getMyGrievances, submitGrievance } from '../api/grievanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { primaryLinkClasses } from '../components/actionStyles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import { CommitteeGrievance, MyGrievance } from '../grievances/GrievanceCaseView'
import { GrievanceStatusTag } from '../grievances/GrievanceStatusTag'
import type { GrievanceSummary } from '../types/grievances'
import { Alert, Button, Card, DataTable, LinkTabs, SelectField, TextAreaField, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

type Notify = (text: string, error?: boolean) => void
export type GrievanceSection = 'mine' | 'new' | 'committee' | 'myCase' | 'committeeCase'

const backLinkClasses =
  'inline-flex min-h-11 items-center gap-1.5 rounded-control font-medium text-stacc-primary hover:underline hover:underline-offset-4 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0'

function AnswerDue({ summary }: { summary: GrievanceSummary }) {
  if (summary.status !== 'SUBMITTED' && summary.status !== 'APPEALED') {
    return <span aria-label="None">–</span>
  }
  return (
    <span className="flex flex-col items-start gap-1">
      <time dateTime={summary.respondBy}>{formatDateTime(summary.respondBy)}</time>
      {summary.overdue && <span className="rounded-sm border border-amber-300 bg-amber-50 px-1.5 py-0.5 text-xs font-medium text-ink">Overdue</span>}
    </span>
  )
}

function CaseTable({ cases, caption, basePath, showSubmitted }: { cases: GrievanceSummary[]; caption: string; basePath: string; showSubmitted: boolean }) {
  return (
    <DataTable<GrievanceSummary>
      caption={caption}
      rows={cases}
      rowKey={(summary) => summary.caseId}
      columns={[
        {
          key: 'case',
          header: 'Case / Subject',
          primary: true,
          cell: (summary) => (
            <>
              <Link to={`${basePath}/${summary.caseId}`} className={`font-semibold ${textLinkClasses}`}>
                {summary.caseNumber}
                <span className="sr-only">: {summary.subject}</span>
              </Link>
              <span aria-hidden="true" className="block break-words text-ink">
                {summary.subject}
              </span>
            </>
          ),
        },
        { key: 'category', header: 'Category', cell: (summary) => summary.categoryName },
        { key: 'status', header: 'Status', cell: (summary) => <GrievanceStatusTag status={summary.status} /> },
        ...(showSubmitted
          ? [
              {
                key: 'submitted',
                header: 'Submitted',
                cell: (summary: GrievanceSummary) => <time dateTime={summary.submittedAt}>{formatDateTime(summary.submittedAt)}</time>,
              },
            ]
          : []),
        { key: 'due', header: 'Answer due', cell: (summary) => <AnswerDue summary={summary} /> },
      ]}
    />
  )
}

function MyGrievances() {
  const mine = useApiQuery(queryKeys.grievances.mine(), (signal) => getMyGrievances(signal))
  return (
    <div className="space-y-5">
      <Alert tone="info">Only you and the category’s committee can open your case.</Alert>
      <Card
        title="My grievances"
        padding="none"
        action={
          <Link to="/grievances/new" className={primaryLinkClasses}>
            Raise a grievance
          </Link>
        }
      >
        {mine.status !== 'loaded' ? (
          <SectionState request={mine} what="your grievances" />
        ) : mine.data.length === 0 ? (
          <EmptyState title="No grievances raised." description="Raise one when something needs the committee’s attention." />
        ) : (
          <CaseTable cases={mine.data} caption="My grievances" basePath="/grievances" showSubmitted={false} />
        )}
      </Card>
    </div>
  )
}

function RaiseGrievance({ notify }: { notify: Notify }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const categories = useApiQuery(queryKeys.grievances.categories(), (signal) => getGrievanceCategories(signal))
  const [form, setForm] = useState({ categoryId: '', subject: '', details: '' })
  const [busy, setBusy] = useState(false)
  return (
    <div className="max-w-3xl space-y-4">
      <Link to="/grievances" className={backLinkClasses}>
        <span aria-hidden="true">←</span> My grievances
      </Link>
      <Card title="Raise a grievance" description="Only the committee for the category you choose can open it. A grievance cannot be edited once submitted.">
        <form
          className="grid gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (form.categoryId === '' || form.subject.trim() === '' || form.details.trim() === '') {
              notify('Choose a category and enter a subject and details.', true)
              return
            }
            setBusy(true)
            void submitGrievance({ categoryId: Number(form.categoryId), subject: form.subject.trim(), details: form.details.trim() })
              .then(async (saved) => {
                await queryClient.invalidateQueries({ queryKey: queryKeys.grievances.all })
                void navigate(`/grievances/${saved.caseId}`, { state: { submitted: saved.caseNumber } })
              })
              .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t submit the grievance.') ?? '', true))
              .finally(() => setBusy(false))
          }}
        >
          <SelectField label="Category" value={form.categoryId} onChange={(event) => setForm({ ...form, categoryId: event.target.value })}>
            <option value="">Choose…</option>
            {categories.status === 'loaded' &&
              categories.data.map((category) => (
                <option key={category.categoryId} value={category.categoryId}>{`${category.name} (answer within ${category.responseDays} days)`}</option>
              ))}
          </SelectField>
          <TextField label="Subject" value={form.subject} maxLength={200} onChange={(event) => setForm({ ...form, subject: event.target.value })} />
          <TextAreaField
            label="Details"
            rows={6}
            value={form.details}
            maxLength={10_000}
            hint="Plain text. Say what happened and what you would like reviewed."
            onChange={(event) => setForm({ ...form, details: event.target.value })}
          />
          <div className="flex flex-wrap gap-3">
            <Button type="submit" disabled={busy}>
              Submit grievance
            </Button>
            <Link to="/grievances" className="inline-flex min-h-11 items-center rounded-control border border-line-strong bg-surface px-4 text-sm font-medium text-ink hover:bg-surface-muted sm:min-h-9">
              Cancel
            </Link>
          </div>
        </form>
      </Card>
    </div>
  )
}

function CommitteeCases() {
  const cases = useApiQuery(queryKeys.grievances.committee(), (signal) => getCommitteeGrievances(signal))
  return (
    <Card title="Committee cases" description="Cases for the categories whose committee you are on. Keep them confidential." padding="none">
      {cases.status !== 'loaded' ? (
        <SectionState request={cases} what="committee cases" />
      ) : cases.data.length === 0 ? (
        <EmptyState title="No cases for your committees." />
      ) : (
        <CaseTable cases={cases.data} caption="Committee cases" basePath="/grievances/committee" showSubmitted />
      )}
    </Card>
  )
}

export function GrievancesPage({ section = 'mine' }: { section?: GrievanceSection }) {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const complainant = hasAnyRole(roles, ['STUDENT', 'FACULTY'])
  const committee = hasAnyRole(roles, ['FACULTY', 'ADMIN'])
  const { caseId } = useParams()
  const location = useLocation()
  const submitted = (location.state as { submitted?: string } | null)?.submitted
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string; at: string } | null>(null)
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text, at: location.pathname })
  const shown = message?.at === location.pathname ? message : null
  const id = Number(caseId)
  const current = section === 'mine' && !complainant ? 'committee' : section

  let body
  if (!complainant && !committee) {
    body = <AccessDeniedState title="Grievances are for student, faculty, and admin accounts." />
  } else if (current === 'mine') {
    body = <MyGrievances />
  } else if (current === 'new') {
    body = complainant ? <RaiseGrievance notify={notify} /> : <AccessDeniedState title="Only students and faculty raise grievances." />
  } else if (current === 'committee') {
    body = committee ? <CommitteeCases /> : <AccessDeniedState title="Committee cases are for faculty and admin accounts." />
  } else if (!Number.isInteger(id) || id <= 0) {
    body = <EmptyState title="Grievance unavailable" description="This case could not be found or is not open to your account." />
  } else if (current === 'myCase') {
    body = <MyGrievance caseId={id} notify={notify} />
  } else {
    body = <CommitteeGrievance caseId={id} notify={notify} />
  }

  return (
    <>
      <title>Grievances · Stacc</title>
      <h1 className="text-3xl font-bold tracking-tight text-portal-navy">Grievances</h1>
      <p className="mt-2 mb-5 text-[0.95rem] leading-6 text-ink-muted sm:text-base">
        Confidential complaints, answered by the committee for each category.
      </p>
      {complainant && committee && (
        <LinkTabs
          label="Grievances"
          tabs={[
            { to: '/grievances', label: 'My grievances', current: current === 'mine' || current === 'new' || current === 'myCase' },
            { to: '/grievances/committee', label: 'Committee cases', current: current === 'committee' || current === 'committeeCase' },
          ]}
        />
      )}
      <div className="space-y-4">
        <p role="status" className="sr-only">
          {shown?.tone === 'success' ? shown.text : submitted && current === 'myCase' ? `Grievance ${submitted} submitted.` : ''}
        </p>
        {shown && <Alert tone={shown.tone}>{shown.text}</Alert>}
        {!shown && submitted && current === 'myCase' && <Alert tone="success">{`Grievance ${submitted} submitted.`}</Alert>}
        {body}
      </div>
    </>
  )
}
