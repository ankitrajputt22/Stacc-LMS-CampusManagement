import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { addPortfolioItem, decidePortfolioItem, getMyPortfolio, getPortfolioVerifications, hidePortfolioItem, requestPortfolioVerification } from '../api/portfolioApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import { safeUrl } from '../formatting/safeUrl'
import type { PortfolioItem, PortfolioKind, PortfolioRecord, PortfolioVerification } from '../types/portfolio'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

type Notify = (text: string, error?: boolean) => void

const KIND_LABEL: Record<PortfolioKind, string> = {
  ACHIEVEMENT: 'Achievement', PROJECT: 'Project', COURSE: 'Course or training', SKILL: 'Skill', SERVICE: 'Service', OTHER: 'Other',
}
const SOURCE_LABEL: Record<PortfolioRecord['source'], string> = {
  DOCUMENT: 'Document', EVENT_ATTENDED: 'Event', VOLUNTEERED: 'Volunteering', TEAM: 'Team',
}
const VERIFICATION_LABEL: Record<PortfolioVerification, string> = { NONE: 'Not verified', REQUESTED: 'Waiting for verification', VERIFIED: 'Verified', DECLINED: 'Declined' }

function useRun(notify: Notify) {
  const queryClient = useQueryClient()
  return (action: () => Promise<unknown>, done: string, fallback: string) =>
    void action()
      .then(async () => {
        notify(done)
        await queryClient.invalidateQueries({ queryKey: queryKeys.portfolio.all })
      })
      .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
}

function Item({ item, notify }: { item: PortfolioItem; notify: Notify }) {
  const run = useRun(notify)
  const [verifier, setVerifier] = useState('')
  const link = item.link ? safeUrl(item.link) : null
  return (
    <li className="space-y-2 px-4 py-3 sm:px-5">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold break-words text-ink">{item.title}</span>
        <span className="text-ink-muted">{KIND_LABEL[item.kind]}</span>
        {item.occurredOn && <span className="text-ink-muted">{item.occurredOn}</span>}
        <Badge tone={item.verification === 'VERIFIED' ? 'success' : item.verification === 'DECLINED' ? 'warning' : 'neutral'}>
          {VERIFICATION_LABEL[item.verification]}
          {item.verifierLoginId && item.verification !== 'NONE' ? ` · ${item.verifierLoginId}` : ''}
        </Badge>
      </div>
      {item.description && <p className="text-sm break-words whitespace-pre-wrap text-ink">{item.description}</p>}
      {link && (
        <a className={textLinkClasses} href={link} target="_blank" rel="noopener noreferrer">
          {item.link}
        </a>
      )}
      {item.verificationNote && <p className="text-sm text-ink-muted">Note: {item.verificationNote}</p>}
      <div className="flex flex-wrap items-end gap-2">
        {item.verification === 'NONE' && (
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              if (verifier.trim() !== '') {
                run(() => requestPortfolioVerification(item.itemId, verifier.trim()), `Sent to ${verifier.trim()} for verification.`, 'Couldn’t ask for verification.')
              }
            }}
          >
            <TextField label={`Faculty employee ID to verify ${item.title}`} value={verifier} maxLength={100} onChange={(event) => setVerifier(event.target.value)} />
            <Button type="submit" size="sm" variant="secondary">Ask to verify</Button>
          </form>
        )}
        <Button size="sm" variant="secondary" onClick={() => run(() => hidePortfolioItem(item.itemId), 'Item hidden from your portfolio.', 'Couldn’t hide the item.')}>
          Hide<span className="sr-only">{` ${item.title}`}</span>
        </Button>
      </div>
    </li>
  )
}

function Student({ notify }: { notify: Notify }) {
  const portfolio = useApiQuery(queryKeys.portfolio.mine(), (signal) => getMyPortfolio(signal))
  const run = useRun(notify)
  const [form, setForm] = useState({ kind: 'ACHIEVEMENT' as PortfolioKind, title: '', description: '', occurredOn: '', link: '' })
  if (portfolio.status !== 'loaded') {
    return <SectionState request={portfolio} what="your portfolio" />
  }
  return (
    <>
      <Card title="From college records" description="Read from the records themselves each time, so they cannot be edited here." padding="none">
        {portfolio.data.records.length === 0 ? (
          <EmptyState title="No records yet." description="Issued documents, events you attended, volunteering, and teams appear here." />
        ) : (
          <ul className="divide-y divide-line text-sm">
            {portfolio.data.records.map((record, index) => (
              <li key={`${record.source}-${index}`} className="flex flex-wrap items-center gap-2 px-4 py-3 sm:px-5">
                <Badge tone="info">{SOURCE_LABEL[record.source]}</Badge>
                <span className="font-medium break-words text-ink">{record.title}</span>
                <span className="text-ink-muted">{record.detail}</span>
                {record.at && <time className="text-ink-muted" dateTime={record.at}>{formatDateTime(record.at)}</time>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Add to your portfolio">
        <form
          className="grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            run(
              () => addPortfolioItem({ kind: form.kind, title: form.title.trim(), description: form.description.trim() || null, occurredOn: form.occurredOn || null, link: form.link.trim() || null })
                .then(() => setForm({ kind: form.kind, title: '', description: '', occurredOn: '', link: '' })),
              'Item added.',
              'Couldn’t add the item.',
            )
          }}
        >
          <SelectField label="Kind" value={form.kind} onChange={(event) => setForm({ ...form, kind: event.target.value as PortfolioKind })}>
            {(Object.keys(KIND_LABEL) as PortfolioKind[]).map((kind) => <option key={kind} value={kind}>{KIND_LABEL[kind]}</option>)}
          </SelectField>
          <TextField label="Title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
          <TextField label="Date (optional)" type="date" value={form.occurredOn} onChange={(event) => setForm({ ...form, occurredOn: event.target.value })} />
          <TextField label="Link (optional)" hint="One https:// address." value={form.link} maxLength={500} onChange={(event) => setForm({ ...form, link: event.target.value })} />
          <div className="sm:col-span-2">
            <TextAreaField label="Description (optional)" rows={3} value={form.description} maxLength={5000} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </div>
          <div><Button type="submit">Add item</Button></div>
        </form>
      </Card>
      <Card title="Your items" padding="none">
        {portfolio.data.items.length === 0 ? (
          <EmptyState title="No items yet." />
        ) : (
          <ul className="divide-y divide-line">{portfolio.data.items.map((item) => <Item key={item.itemId} item={item} notify={notify} />)}</ul>
        )}
      </Card>
    </>
  )
}

function Verifier({ notify }: { notify: Notify }) {
  const waiting = useApiQuery(queryKeys.portfolio.verifications(), (signal) => getPortfolioVerifications(signal))
  const run = useRun(notify)
  const [notes, setNotes] = useState<Record<number, string>>({})
  return (
    <Card title="Verification requests" description="Students who named you to verify an item in their portfolio." padding="none">
      {waiting.status !== 'loaded' ? (
        <SectionState request={waiting} what="verification requests" />
      ) : waiting.data.length === 0 ? (
        <EmptyState title="Nothing waiting for you." />
      ) : (
        <ul className="divide-y divide-line">
          {waiting.data.map((item) => (
            <li key={item.itemId} className="space-y-2 px-4 py-3 sm:px-5">
              <div className="flex flex-wrap items-center gap-2 text-sm">
                <span className="font-mono text-ink">{item.ownerLoginId}</span>
                <span className="font-semibold break-words text-ink">{item.title}</span>
                <span className="text-ink-muted">{KIND_LABEL[item.kind]}</span>
                {item.occurredOn && <span className="text-ink-muted">{item.occurredOn}</span>}
              </div>
              {item.description && <p className="text-sm break-words whitespace-pre-wrap text-ink">{item.description}</p>}
              <TextField label={`Note for ${item.ownerLoginId} (optional)`} value={notes[item.itemId] ?? ''} maxLength={1000} onChange={(event) => setNotes({ ...notes, [item.itemId]: event.target.value })} />
              <div className="flex gap-2">
                <Button size="sm" onClick={() => run(() => decidePortfolioItem(item.itemId, true, notes[item.itemId]?.trim() || null), 'Item verified.', 'Couldn’t save the decision.')}>
                  Verify<span className="sr-only">{` ${item.title}`}</span>
                </Button>
                <Button size="sm" variant="secondary" onClick={() => run(() => decidePortfolioItem(item.itemId, false, notes[item.itemId]?.trim() || null), 'Item declined.', 'Couldn’t save the decision.')}>
                  Decline<span className="sr-only">{` ${item.title}`}</span>
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** /portfolio: a student's records and own items, and a faculty member's verification requests. */
export function PortfolioPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text })
  return (
    <>
      <title>Portfolio · Stacc</title>
      <PageHeader title="Portfolio" description="What you have done at college: records from the college, and items you add and get verified." />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {hasAnyRole(roles, ['STUDENT']) && <Student notify={notify} />}
        {hasAnyRole(roles, ['FACULTY']) && <Verifier notify={notify} />}
      </div>
    </>
  )
}
