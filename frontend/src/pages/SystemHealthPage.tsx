import { getAiHealth, getSystemHealth } from '../api/systemApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { Badge, Button, Card, PageHeader } from '../ui'

const PART_LABEL: Record<string, string> = {
  db: 'Database', storage: 'File storage', aiService: 'AI service', diskSpace: 'Disk space', ping: 'Application',
  livenessState: 'Running', readinessState: 'Ready for requests', ssl: 'Certificates', mail: 'Email',
}

function tone(status: string) {
  return status === 'UP' ? 'success' : status === 'DOWN' || status === 'OUT_OF_SERVICE' ? 'danger' : 'neutral'
}

const WORDS: Record<string, string> = {
  READY: 'Ready', BUILDING: 'Building', FAILED: 'Failed', QUEUED: 'Queued', RUNNING: 'Running', SUCCEEDED: 'Succeeded',
  INSUFFICIENT_CONTEXT: 'No source found', REFUSED: 'Refused', BUSY: 'Busy', ASK: 'Questions', SUMMARY: 'Summaries',
  PRACTICE: 'Practice', ASSESSMENT_DRAFT: 'Assessment drafts',
}

function Counts({ label, counts }: { label: string; counts: Record<string, number> }) {
  const entries = Object.entries(counts)
  return (
    <div>
      <dt className="text-xs font-medium text-ink-muted">{label}</dt>
      <dd className="text-ink">{entries.length === 0 ? 'None' : entries.map(([key, count]) => `${WORDS[key] ?? key} ${count}`).join(' · ')}</dd>
    </div>
  )
}

/** The AI part (v0.13.7): numbers only, never settings, courses, people, or content. */
function AiCard() {
  const ai = useApiQuery(queryKeys.system.ai(), (signal) => getAiHealth(signal))
  if (ai.status !== 'loaded') {
    return <Card title="AI"><SectionState request={ai} what="AI health" /></Card>
  }
  const data = ai.data
  return (
    <Card title="AI">
      {!data.enabled ? (
        <p className="text-sm text-ink-muted">AI features are off.</p>
      ) : (
        <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
          <div><dt className="text-xs font-medium text-ink-muted">AI service</dt><dd><Badge tone={data.reachable ? 'success' : 'danger'}>{data.reachable ? 'Answering' : 'Not answering'}</Badge></dd></div>
          <div><dt className="text-xs font-medium text-ink-muted">Requests now</dt><dd className="text-ink">{data.running} running · {data.waiting} waiting</dd></div>
          <Counts label="Course knowledge" counts={data.courseIndexes} />
          <Counts label="Teacher documents" counts={data.teacherIndexes} />
          <Counts label="Refresh jobs (live and last 24 hours)" counts={data.jobs} />
          <Counts label="Requests in the last 24 hours" counts={data.requestsByOutcome} />
          <Counts label="Requests by kind" counts={data.requestsByKind} />
          <div><dt className="text-xs font-medium text-ink-muted">Average answer time</dt><dd className="text-ink">{data.averageMillis === null ? '—' : `${(data.averageMillis / 1000).toFixed(1)} s`}</dd></div>
          <div><dt className="text-xs font-medium text-ink-muted">Courses with the student assistant off</dt><dd className="text-ink">{data.coursesWithStudentAiOff}</dd></div>
        </dl>
      )}
    </Card>
  )
}

function hours(minutes: number) {
  return minutes < 60 ? `${minutes} min` : `${Math.floor(minutes / 60)} h ${minutes % 60} min`
}

/** /admin/system: each part's status, the schema version, optional features, and the JVM. Loaded on request only. */
export function SystemHealthPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const health = useApiQuery(isAdmin ? queryKeys.system.health() : null, (signal) => getSystemHealth(signal))
  return (
    <>
      <title>System health · Stacc</title>
      <PageHeader title="System health" description="How each part of Stacc is doing right now. AI and file storage may be down without stopping the rest." />
      <div className="mt-6 space-y-6">
        {!isAdmin ? (
          <StatePanel><AccessDeniedState title="System health is for admin accounts." /></StatePanel>
        ) : health.status !== 'loaded' ? (
          <SectionState request={health} what="system health" />
        ) : (
          <>
            <div className="flex flex-wrap items-center gap-3">
              <Badge tone={tone(health.data.status)}>{`Overall: ${health.data.status}`}</Badge>
              <Button size="sm" variant="secondary" onClick={health.refresh}>Check again</Button>
            </div>
            <Card title="Parts">
              <ul className="grid gap-2 text-sm sm:grid-cols-2">
                {Object.entries(health.data.parts).map(([part, status]) => (
                  <li key={part} className="flex items-center justify-between gap-2 rounded-control border border-line px-3 py-2">
                    <span className="text-ink">{PART_LABEL[part] ?? part}</span>
                    <Badge tone={tone(status)}>{status}</Badge>
                  </li>
                ))}
              </ul>
            </Card>
            <Card title="Application">
              <dl className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
                <div><dt className="text-xs font-medium text-ink-muted">Database schema</dt><dd className="text-ink">{health.data.schemaVersion ?? '—'} ({health.data.migrations} migrations)</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">Started</dt><dd className="text-ink">{formatDateTime(health.data.startedAt)}</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">Up for</dt><dd className="text-ink">{hours(health.data.uptimeMinutes)}</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">Memory</dt><dd className="text-ink">{health.data.heapMaxMb < 0 ? `${health.data.heapUsedMb} MB` : `${health.data.heapUsedMb} of ${health.data.heapMaxMb} MB`}</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">Processors and threads</dt><dd className="text-ink">{health.data.processors} · {health.data.threads}</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">Profiles</dt><dd className="text-ink">{health.data.profiles.length === 0 ? 'default' : health.data.profiles.join(', ')}</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">Email</dt><dd className="text-ink">{health.data.mailEnabled ? 'On' : 'Off'}</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">AI features</dt><dd className="text-ink">{health.data.aiEnabled ? 'On' : 'Off'}</dd></div>
                <div><dt className="text-xs font-medium text-ink-muted">Metrics for Prometheus</dt><dd className="text-ink">{health.data.metricsEnabled ? 'On (internal port)' : 'Off'}</dd></div>
              </dl>
            </Card>
            <AiCard />
          </>
        )}
      </div>
    </>
  )
}
