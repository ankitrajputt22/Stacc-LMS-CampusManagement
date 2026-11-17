import { getAiHealth, getSystemHealth } from '../api/systemApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { useId } from 'react'
import { Badge, Button, Card, PageHeader } from '../ui'

const PART_LABEL: Record<string, string> = {
  db: 'Database', storage: 'File storage', aiService: 'AI service', diskSpace: 'Disk space', ping: 'Application',
  livenessState: 'Running', readinessState: 'Ready for requests', ssl: 'Certificates', mail: 'Email',
}

function tone(status: string) {
  return status === 'UP' ? 'success' : status === 'DOWN' || status === 'OUT_OF_SERVICE' ? 'danger' : 'neutral'
}

const STATUS_CLASSES = {
  success: 'bg-success-soft text-ink',
  danger: 'bg-danger/10 text-danger',
  neutral: 'bg-surface-muted text-ink-muted',
} as const

function StatusPill({ status, large = false }: { status: string; large?: boolean }) {
  const kind = tone(status)
  return (
    <span className={`inline-flex items-center gap-2 rounded-control font-bold ${STATUS_CLASSES[kind]} ${large ? 'px-6 py-3 text-2xl' : 'min-w-28 justify-center px-4 py-1.5 text-base'}`}>
      {kind === 'success' ? <span aria-hidden="true">✓</span> : kind === 'danger' ? <span aria-hidden="true">✕</span> : null}
      {status}
    </span>
  )
}

const WORDS: Record<string, string> = {
  READY: 'Ready', BUILDING: 'Building', FAILED: 'Failed', QUEUED: 'Queued', RUNNING: 'Running', SUCCEEDED: 'Succeeded',
  INSUFFICIENT_CONTEXT: 'No source found', REFUSED: 'Refused', BUSY: 'Busy', ASK: 'Questions', SUMMARY: 'Summaries',
  PRACTICE: 'Practice', ASSESSMENT_DRAFT: 'Assessment drafts',
}

function Heading({ id, children }: { id: string; children: string }) {
  return (
    <div>
      <h2 id={id} className="text-2xl font-bold text-ink">{children}</h2>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-12 rounded bg-portal-cta" />
    </div>
  )
}

function NumberRow({ title, figures }: { title: string; figures: [string, number | string][] }) {
  const id = useId()
  return (
    <section aria-labelledby={id} className="rounded-panel border border-line bg-surface p-4 sm:px-5">
      <h3 id={id} className="text-xl font-bold text-ink">{title}</h3>
      {figures.length === 0 ? (
        <p className="mt-2 text-[0.95rem] text-ink-muted">None</p>
      ) : (
        <dl className="mt-2 flex flex-wrap gap-y-3">
          {figures.map(([label, value]) => (
            <div key={label} className="flex min-w-28 flex-1 flex-col-reverse border-line px-4 not-first:border-l first:pl-2">
              <dt className="text-[0.95rem] text-ink">{label}</dt>
              <dd className="text-3xl font-bold text-ink tabular-nums">{value}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
  )
}

function words(counts: Record<string, number>): [string, number][] {
  return Object.entries(counts).map(([key, count]) => [WORDS[key] ?? key, count])
}

function AiCard() {
  const ai = useApiQuery(queryKeys.system.ai(), (signal) => getAiHealth(signal))
  if (ai.status !== 'loaded') {
    return <Card title="AI"><SectionState request={ai} what="AI health" /></Card>
  }
  const data = ai.data
  if (!data.enabled) {
    return <Card title="AI"><p className="text-[0.95rem] text-ink-muted">AI features are off.</p></Card>
  }
  return (
    <section aria-labelledby="system-ai" className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <h2 id="system-ai" className="text-2xl font-bold text-ink">AI</h2>
        <span className="text-[0.95rem] text-ink">AI service:</span>
        <Badge tone={data.reachable ? 'success' : 'danger'}>{data.reachable ? 'Answering' : 'Not answering'}</Badge>
      </div>
      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)] lg:items-start">
        <div className="space-y-4">
          <NumberRow title="Requests now" figures={[['Running', data.running], ['Waiting', data.waiting]]} />
          <NumberRow title="Refresh jobs (live and last 24 hours)" figures={words(data.jobs)} />
          <NumberRow title="Requests in the last 24 hours" figures={words(data.requestsByOutcome)} />
          <NumberRow title="Requests by kind" figures={words(data.requestsByKind)} />
          <NumberRow
            title="Answers and courses"
            figures={[
              ['Average answer time', data.averageMillis === null ? '—' : `${(data.averageMillis / 1000).toFixed(1)} s`],
              ['Courses with the student assistant off', data.coursesWithStudentAiOff],
            ]}
          />
        </div>
        <div className="rounded-panel border border-line bg-surface p-4 sm:p-5">
          <h3 className="text-xl font-bold text-ink">AI knowledge</h3>
          <dl className="mt-2 divide-y divide-line">
            {[
              ...words(data.courseIndexes).map(([label, value]): [string, number] => [`Course knowledge: ${label.toLowerCase()}`, value]),
              ...words(data.teacherIndexes).map(([label, value]): [string, number] => [`Teacher documents: ${label.toLowerCase()}`, value]),
            ].map(([label, value]) => (
              <div key={label} className="flex items-baseline justify-between gap-4 py-3">
                <dt className="text-[0.95rem] text-ink">{label}</dt>
                <dd className="text-2xl font-bold text-ink tabular-nums">{value}</dd>
              </div>
            ))}
          </dl>
          {Object.keys(data.courseIndexes).length + Object.keys(data.teacherIndexes).length === 0 && (
            <p className="mt-2 text-[0.95rem] text-ink-muted">No AI knowledge built yet.</p>
          )}
        </div>
      </div>
    </section>
  )
}

function hours(minutes: number) {
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`
}

export function SystemHealthPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const health = useApiQuery(isAdmin ? queryKeys.system.health() : null, (signal) => getSystemHealth(signal))
  return (
    <>
      <title>System health · Stacc</title>
      <PageHeader title="System Health" description="How each part of Stacc is doing right now. AI and file storage may be down without stopping the rest." />
      <div className="mt-6 space-y-6">
        {!isAdmin ? (
          <StatePanel><AccessDeniedState title="System health is for admin accounts." /></StatePanel>
        ) : health.status !== 'loaded' ? (
          <SectionState request={health} what="system health" />
        ) : (
          <>
            <div className="grid gap-4 lg:grid-cols-2 lg:items-start">
              <div className="rounded-panel border border-line bg-surface p-4 sm:p-5">
                <Heading id="system-overall">Overall</Heading>
                <div className="mt-4 flex flex-wrap items-center gap-4">
                  <StatusPill status={health.data.status} large />
                  <span className="sr-only">{`Overall: ${health.data.status}`}</span>
                  <Button onClick={health.refresh}>Check again</Button>
                </div>
                <section aria-labelledby="system-parts" className="mt-6 border-t border-line pt-5">
                  <Heading id="system-parts">Parts</Heading>
                  <ul aria-label="Parts" className="mt-4 divide-y divide-line rounded-panel border border-line">
                    {Object.entries(health.data.parts).map(([part, status]) => (
                      <li key={part} className="flex items-center justify-between gap-3 px-4 py-2.5">
                        <span className="text-lg font-semibold text-ink">{PART_LABEL[part] ?? part}</span>
                        <StatusPill status={status} />
                      </li>
                    ))}
                  </ul>
                </section>
              </div>
              <section aria-labelledby="system-application" className="rounded-panel border border-line bg-surface p-4 sm:p-5">
                <Heading id="system-application">Application</Heading>
                <dl className="mt-3 divide-y divide-line">
                  {[
                    ['Database schema', `${health.data.schemaVersion ?? '—'} (${health.data.migrations} migrations)`],
                    ['Started', formatDateTime(health.data.startedAt)],
                    ['Up for', hours(health.data.uptimeMinutes)],
                    ['Memory', health.data.heapMaxMb < 0 ? `${health.data.heapUsedMb} MB` : `${health.data.heapUsedMb} of ${health.data.heapMaxMb} MB`],
                    ['Processors and threads', `${health.data.processors} · ${health.data.threads}`],
                    ['Profiles', health.data.profiles.length === 0 ? 'default' : health.data.profiles.join(', ')],
                    ['Email', health.data.mailEnabled ? 'On' : 'Off'],
                    ['AI features', health.data.aiEnabled ? 'On' : 'Off'],
                    ['Metrics for Prometheus', health.data.metricsEnabled ? 'On (internal port)' : 'Off'],
                  ].map(([label, value]) => (
                    <div key={label} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-4 py-3 text-[0.95rem]">
                      <dt className="font-bold text-ink">{label}</dt>
                      <dd className="break-words text-ink">{value}</dd>
                    </div>
                  ))}
                </dl>
              </section>
            </div>
            <AiCard />
          </>
        )}
      </div>
    </>
  )
}
