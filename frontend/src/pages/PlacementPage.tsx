import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  addCompany,
  createOpportunity,
  exportApplicants,
  getApplicants,
  getCompanies,
  getManagedOpportunities,
  getPlacementPolicy,
  makeOffer,
  moveApplicant,
  scheduleRound,
  setOpportunityOpen,
  setPlacementPolicy,
} from '../api/careerApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { saveBlob } from '../courses/materials'
import { SectionState } from '../courses/SectionState'
import { failureText, localToIso } from '../engagement/engagementText'
import type { Opportunity, OpportunityKind } from '../types/career'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

type Notify = (text: string, error?: boolean) => void

function Applicants({ opportunity, notify }: { opportunity: Opportunity; notify: Notify }) {
  const applicants = useApiQuery(queryKeys.career.applicants(opportunity.opportunityId), (signal) => getApplicants(opportunity.opportunityId, signal))
  const [offerFor, setOfferFor] = useState<number | null>(null)
  const [role, setRole] = useState('')
  const [pay, setPay] = useState('')
  if (applicants.status !== 'loaded') {
    return <SectionState request={applicants} what="applicants" />
  }
  if (applicants.data.length === 0) {
    return <p className="text-sm text-ink-muted">No applicants yet.</p>
  }
  return (
    <ul className="divide-y divide-line rounded-control border border-line text-sm">
      {applicants.data.map((a) => (
        <li key={a.applicationId} className="space-y-2 px-3 py-2">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-ink">{a.applicantLoginId}</span>
            <Badge tone="neutral">{a.status.replace('_', ' ').toLowerCase()}</Badge>
            <span className="text-ink-muted">Stage: {a.stage}{a.resumeLabel && ` · resume: ${a.resumeLabel}`}{!a.shareConsent && ' · did not consent to sharing'}</span>
            {a.offer && <Badge tone="success">Offer {a.offer.status.toLowerCase()}</Badge>}
          </div>
          <div className="flex flex-wrap gap-2">
            {(a.status === 'APPLIED' || a.status === 'IN_PROGRESS') && (
              <>
                <Button size="sm" onClick={() => void moveApplicant(a.applicationId, true).then(() => notify(`${a.applicantLoginId} moved on.`)).catch((caught: unknown) => notify(failureText(caught, 'Couldn’t move.') ?? '', true))}>
                  Next stage<span className="sr-only">{` for ${a.applicantLoginId}`}</span>
                </Button>
                <Button size="sm" variant="secondary" onClick={() => void moveApplicant(a.applicationId, false).then(() => notify(`${a.applicantLoginId} not selected.`))}>
                  Not selected<span className="sr-only">{`: ${a.applicantLoginId}`}</span>
                </Button>
              </>
            )}
            {a.status === 'SELECTED' && !a.offer && (offerFor === a.applicationId ? (
              <span className="flex flex-wrap items-end gap-2">
                <TextField label="Role" value={role} maxLength={200} onChange={(event) => setRole(event.target.value)} />
                <TextField label="Compensation (optional)" value={pay} maxLength={200} onChange={(event) => setPay(event.target.value)} />
                <Button size="sm" onClick={() => void makeOffer(a.applicationId, { roleTitle: role.trim(), compensation: pay.trim() || null, respondBy: null }).then(() => { setOfferFor(null); notify('Offer made.') }).catch((caught: unknown) => notify(failureText(caught, 'Couldn’t make the offer.') ?? '', true))}>Send offer</Button>
              </span>
            ) : (
              <Button size="sm" onClick={() => setOfferFor(a.applicationId)}>Make offer<span className="sr-only">{` to ${a.applicantLoginId}`}</span></Button>
            ))}
          </div>
        </li>
      ))}
    </ul>
  )
}

function OpportunityAdmin({ opportunity, notify }: { opportunity: Opportunity; notify: Notify }) {
  const [open, setOpen] = useState(false)
  const [stage, setStage] = useState('0')
  const [when, setWhen] = useState('')
  const [venue, setVenue] = useState('')
  return (
    <li className="space-y-2 px-4 py-4 text-sm sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{opportunity.title}</span>
        <Badge tone={opportunity.status === 'OPEN' ? 'success' : 'neutral'}>{opportunity.status.toLowerCase()}</Badge>
        <span className="text-ink-muted">{opportunity.kind.toLowerCase()} · {opportunity.company ?? opportunity.organization ?? ''} · by {formatDateTime(opportunity.deadline)} · {opportunity.applicants} applicants</span>
      </div>
      <div className="flex flex-wrap gap-2">
        {opportunity.status === 'DRAFT' && <Button size="sm" onClick={() => void setOpportunityOpen(opportunity.opportunityId, true).then(() => notify('Opened for applications.'))}>Open</Button>}
        {opportunity.status === 'OPEN' && <Button size="sm" variant="secondary" onClick={() => void setOpportunityOpen(opportunity.opportunityId, false).then(() => notify('Closed.'))}>Close</Button>}
        <Button size="sm" variant="secondary" onClick={() => setOpen(!open)}>{open ? 'Hide applicants' : 'Applicants and rounds'}</Button>
        <Button size="sm" variant="secondary" onClick={() => void exportApplicants(opportunity.opportunityId).then((blob) => saveBlob(blob, `applicants-${opportunity.opportunityId}.csv`)).catch(() => notify('Couldn’t export.', true))}>
          Export consenting applicants (CSV)
        </Button>
      </div>
      {open && (
        <div className="space-y-3">
          <div className="grid gap-2 sm:grid-cols-[12rem_1fr_1fr_auto] sm:items-end">
            <SelectField label="Stage" value={stage} onChange={(event) => setStage(event.target.value)}>
              {opportunity.stages.map((name, index) => <option key={name} value={index}>{name}</option>)}
            </SelectField>
            <TextField label="When" type="datetime-local" value={when} onChange={(event) => setWhen(event.target.value)} />
            <TextField label="Venue" value={venue} maxLength={200} onChange={(event) => setVenue(event.target.value)} />
            <Button variant="secondary" onClick={() => {
              const startsAt = localToIso(when)
              if (!startsAt) {
                notify('Choose when the round is.', true)
                return
              }
              void scheduleRound(opportunity.opportunityId, { stageIndex: Number(stage), startsAt, venue: venue.trim() || null }).then(() => notify('Round scheduled.'))
            }}>Schedule round</Button>
          </div>
          {opportunity.rounds.map((round) => <p key={round.stageIndex} className="text-ink-muted">{round.stage}: {formatDateTime(round.startsAt)}{round.venue && ` · ${round.venue}`}</p>)}
          <Applicants opportunity={opportunity} notify={notify} />
        </div>
      )}
    </li>
  )
}

/** /placement: the placement office's companies, opportunities, rounds, applicants, offers, policy, and consent-based export. */
export function PlacementPage() {
  const queryClient = useQueryClient()
  const companies = useApiQuery(queryKeys.career.companies(), (signal) => getCompanies(signal))
  const managed = useApiQuery(queryKeys.career.managed(), (signal) => getManagedOpportunities(signal))
  const policy = useApiQuery(queryKeys.career.policy(), (signal) => getPlacementPolicy(signal))
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const [company, setCompany] = useState({ name: '', website: '' })
  const [form, setForm] = useState({ kind: 'PLACEMENT' as OpportunityKind, title: '', companyId: '', organization: '', description: '', deadline: '', stages: 'Applied\nTest\nInterview' })
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    if (!error) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.career.all })
    }
  }
  return (
    <div className="max-w-5xl">
      <title>Placement · Stacc</title>
      <PageHeader title="Placement office" description="Companies, opportunities, rounds, applicants, and offers." />
      <div className="mt-6 space-y-6">
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        <Card title="Placement policy">
          {policy.status === 'loaded' ? (
            <label className="inline-flex items-center gap-2 text-sm text-ink">
              <input type="checkbox" className="size-4" checked={policy.data.oneAcceptedPlacement}
                onChange={(event) => void setPlacementPolicy(event.target.checked).then(() => notify('Policy saved.'))} />
              A student who accepted a placement offer cannot apply to or accept another placement
            </label>
          ) : <SectionState request={policy} what="the policy" />}
        </Card>
        <Card title="New opportunity">
          <form className="grid gap-3 sm:grid-cols-2" onSubmit={(event) => {
            event.preventDefault()
            const deadline = localToIso(form.deadline)
            if (!deadline || form.title.trim() === '') {
              notify('Give a title and a deadline.', true)
              return
            }
            void createOpportunity({ kind: form.kind, title: form.title.trim(), companyId: form.companyId === '' ? null : Number(form.companyId),
              organization: form.organization.trim() || null, description: form.description.trim() || null, programIds: [], deadline,
              stages: form.stages.split('\n').map((s) => s.trim()).filter(Boolean) })
              .then(() => notify('Draft saved; open it when ready.'))
              .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t save.') ?? '', true))
          }}>
            <SelectField label="Kind" value={form.kind} onChange={(event) => setForm({ ...form, kind: event.target.value as OpportunityKind })}>
              <option value="PLACEMENT">Placement</option><option value="INTERNSHIP">Internship</option><option value="RESEARCH">Research</option><option value="SCHOLARSHIP">Scholarship</option>
            </SelectField>
            <TextField label="Title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
            <SelectField label="Company" value={form.companyId} onChange={(event) => setForm({ ...form, companyId: event.target.value })}>
              <option value="">None</option>
              {companies.status === 'loaded' && companies.data.map((c) => <option key={c.companyId} value={c.companyId}>{c.name}</option>)}
            </SelectField>
            <TextField label="Organization (if no company)" value={form.organization} maxLength={200} onChange={(event) => setForm({ ...form, organization: event.target.value })} />
            <TextField label="Apply by" type="datetime-local" value={form.deadline} onChange={(event) => setForm({ ...form, deadline: event.target.value })} />
            <TextAreaField label="Stages (one per line)" rows={3} value={form.stages} onChange={(event) => setForm({ ...form, stages: event.target.value })} />
            <div className="sm:col-span-2"><TextAreaField label="Description (optional)" rows={2} value={form.description} maxLength={10_000} onChange={(event) => setForm({ ...form, description: event.target.value })} /></div>
            <div><Button type="submit">Save draft</Button></div>
          </form>
        </Card>
        <Card title="Opportunities" padding="none">
          {managed.status !== 'loaded' ? <SectionState request={managed} what="opportunities" /> : managed.data.length === 0 ? (
            <EmptyState title="No opportunities yet." description="Create one above." />
          ) : (
            <ul className="divide-y divide-line">{managed.data.map((o) => <OpportunityAdmin key={o.opportunityId} opportunity={o} notify={notify} />)}</ul>
          )}
        </Card>
        <Card title="Companies">
          <form className="grid gap-2 sm:grid-cols-[1fr_1fr_auto] sm:items-end" onSubmit={(event) => {
            event.preventDefault()
            void addCompany({ name: company.name.trim(), website: company.website.trim() || null, description: null })
              .then(() => { setCompany({ name: '', website: '' }); notify('Company added.') })
              .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t add.') ?? '', true))
          }}>
            <TextField label="Company name" value={company.name} maxLength={200} onChange={(event) => setCompany({ ...company, name: event.target.value })} />
            <TextField label="Website (https)" value={company.website} maxLength={300} onChange={(event) => setCompany({ ...company, website: event.target.value })} />
            <Button type="submit" variant="secondary">Add company</Button>
          </form>
          {companies.status === 'loaded' && companies.data.length > 0 && (
            <p className="mt-3 text-sm text-ink-muted">{companies.data.map((c) => c.name).join(' · ')}</p>
          )}
        </Card>
      </div>
    </div>
  )
}
