import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getCommitteeGrievances, getGrievanceCategories, getMyGrievances, submitGrievance } from '../api/grievanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import { CommitteeGrievance, MyGrievance } from '../grievances/GrievanceCaseView'
import { GRIEVANCE_STATUS_LABEL } from '../grievances/grievanceText'
import type { GrievanceSummary } from '../types/grievances'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

type Notify = (text: string, error?: boolean) => void

function CaseList({ cases, kind, notify }: { cases: GrievanceSummary[]; kind: 'mine' | 'committee'; notify: Notify }) {
  const [open, setOpen] = useState<number | null>(null)
  return (
    <ul className="divide-y divide-line">
      {cases.map((summary) => (
        <li key={summary.caseId} className="space-y-3 px-4 py-3 sm:px-5">
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-mono text-ink-muted">{summary.caseNumber}</span>
            <span className="font-semibold break-words text-ink">{summary.subject}</span>
            <span className="text-ink-muted">{summary.categoryName}</span>
            <Badge tone={summary.status === 'CLOSED' ? 'neutral' : 'info'}>{GRIEVANCE_STATUS_LABEL[summary.status]}</Badge>
            {summary.overdue && <Badge tone="warning">Overdue</Badge>}
            {(summary.status === 'SUBMITTED' || summary.status === 'APPEALED') && (
              <span className="text-ink-muted">
                Answer due <time dateTime={summary.respondBy}>{formatDateTime(summary.respondBy)}</time>
              </span>
            )}
            <Button size="sm" variant="secondary" onClick={() => setOpen(open === summary.caseId ? null : summary.caseId)}>
              {open === summary.caseId ? 'Close' : 'Open'}
              <span className="sr-only">{` ${summary.caseNumber}`}</span>
            </Button>
          </div>
          {open === summary.caseId &&
            (kind === 'mine' ? <MyGrievance caseId={summary.caseId} notify={notify} /> : <CommitteeGrievance caseId={summary.caseId} notify={notify} />)}
        </li>
      ))}
    </ul>
  )
}

function Complainant({ notify }: { notify: Notify }) {
  const queryClient = useQueryClient()
  const categories = useApiQuery(queryKeys.grievances.categories(), (signal) => getGrievanceCategories(signal))
  const mine = useApiQuery(queryKeys.grievances.mine(), (signal) => getMyGrievances(signal))
  const [form, setForm] = useState({ categoryId: '', subject: '', details: '' })
  return (
    <>
      <Card title="Raise a grievance" description="Only the committee for the category you choose can open it.">
        <form
          className="grid gap-3"
          onSubmit={(event) => {
            event.preventDefault()
            if (form.categoryId === '' || form.subject.trim() === '' || form.details.trim() === '') {
              notify('Choose a category and enter a subject and details.', true)
              return
            }
            void submitGrievance({ categoryId: Number(form.categoryId), subject: form.subject.trim(), details: form.details.trim() })
              .then(async (saved) => {
                setForm({ categoryId: '', subject: '', details: '' })
                notify(`Grievance ${saved.caseNumber} submitted.`)
                await queryClient.invalidateQueries({ queryKey: queryKeys.grievances.all })
              })
              .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t submit the grievance.') ?? '', true))
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
          <TextAreaField label="Details" rows={5} value={form.details} maxLength={10_000} onChange={(event) => setForm({ ...form, details: event.target.value })} />
          <div><Button type="submit">Submit grievance</Button></div>
        </form>
      </Card>
      <Card title="My grievances" padding="none">
        {mine.status !== 'loaded' ? (
          <SectionState request={mine} what="your grievances" />
        ) : mine.data.length === 0 ? (
          <EmptyState title="No grievances raised." />
        ) : (
          <CaseList cases={mine.data} kind="mine" notify={notify} />
        )}
      </Card>
    </>
  )
}

function Committee({ notify }: { notify: Notify }) {
  const cases = useApiQuery(queryKeys.grievances.committee(), (signal) => getCommitteeGrievances(signal))
  return (
    <Card title="Committee cases" description="Cases of the categories whose committee you are on. Keep them confidential." padding="none">
      {cases.status !== 'loaded' ? (
        <SectionState request={cases} what="committee cases" />
      ) : cases.data.length === 0 ? (
        <EmptyState title="No cases for your committees." />
      ) : (
        <CaseList cases={cases.data} kind="committee" notify={notify} />
      )}
    </Card>
  )
}

/** /grievances: raise and follow one's own grievances, and, for committee members, the cases of their committees. */
export function GrievancesPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text })
  return (
    <>
      <title>Grievances · Stacc</title>
      <PageHeader title="Grievances" description="Confidential complaints, answered by the committee for each category." />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {hasAnyRole(roles, ['STUDENT', 'FACULTY']) && <Complainant notify={notify} />}
        {hasAnyRole(roles, ['FACULTY', 'ADMIN']) && <Committee notify={notify} />}
      </div>
    </>
  )
}
