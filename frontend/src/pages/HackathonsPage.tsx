import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  addHackathonJudge,
  createHackathon,
  formHackathonTeam,
  getHackathons,
  getJudging,
  getOfficeHackathons,
  joinHackathonTeam,
  leaveHackathonTeam,
  publishHackathon,
  releaseHackathonResults,
  scoreSubmission,
  submitHackathonWork,
} from '../api/hackathonApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText, localToIso } from '../engagement/engagementText'
import { safeUrl } from '../formatting/safeUrl'
import type { Hackathon, HackathonResult, JudgeItem } from '../types/hackathons'
import { Alert, Badge, Button, Card, DataTable, PageHeader, TextAreaField, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

type Run = (action: () => Promise<unknown>, done: string, fallback: string) => void

function Results({ title, rows }: { title: string; rows: HackathonResult[] }) {
  return (
    <DataTable<HackathonResult>
      caption={`Results of ${title}`}
      rows={rows}
      rowKey={(row) => row.teamName}
      empty={<p className="text-sm text-ink-muted">No submissions.</p>}
      columns={[
        { key: 'place', header: 'Place', cell: (row) => row.place },
        { key: 'team', header: 'Team', primary: true, cell: (row) => row.teamName },
        { key: 'work', header: 'Submission', cell: (row) => row.submissionTitle },
        { key: 'total', header: 'Total score', cell: (row) => row.total },
      ]}
    />
  )
}

function StudentHackathon({ hackathon, run }: { hackathon: Hackathon; run: Run }) {
  const [teamName, setTeamName] = useState('')
  const myTeam = hackathon.teams.find((team) => team.teamId === hackathon.myTeamId) ?? null
  const [work, setWork] = useState({ title: myTeam?.submission?.title ?? '', summary: myTeam?.submission?.summary ?? '', link: myTeam?.submission?.link ?? '' })
  const link = myTeam?.submission?.link ? safeUrl(myTeam.submission.link) : null
  return (
    <Card title={hackathon.title} description={`${formatDateTime(hackathon.startsAt)} to ${formatDateTime(hackathon.endsAt)} · teams of up to ${hackathon.maxTeamSize}`}>
      <div className="space-y-4">
        {hackathon.description && <p className="text-sm break-words whitespace-pre-wrap text-ink">{hackathon.description}</p>}
        {hackathon.status === 'RESULTS_RELEASED' && <Results title={hackathon.title} rows={hackathon.results} />}
        <ul className="space-y-1 text-sm">
          {hackathon.teams.map((team) => (
            <li key={team.teamId} className="flex flex-wrap items-center gap-2">
              <span className="font-medium text-ink">{team.name}</span>
              <span className="text-ink-muted">{team.memberLoginIds.join(', ')}</span>
              {team.submitted && <Badge tone="success">Submitted</Badge>}
              {hackathon.takingWork && hackathon.myTeamId === null && team.memberLoginIds.length < hackathon.maxTeamSize && (
                <Button size="sm" variant="secondary" onClick={() => run(() => joinHackathonTeam(team.teamId), `You joined ${team.name}.`, 'Couldn’t join the team.')}>
                  Join<span className="sr-only">{` ${team.name}`}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
        {hackathon.takingWork && hackathon.myTeamId === null && (
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              run(() => formHackathonTeam(hackathon.hackathonId, teamName.trim()), 'Team formed.', 'Couldn’t form the team.')
            }}
          >
            <TextField label={`New team name for ${hackathon.title}`} value={teamName} maxLength={100} onChange={(event) => setTeamName(event.target.value)} />
            <Button type="submit" size="sm">Form team</Button>
          </form>
        )}
        {myTeam && (
          <div className="space-y-3 rounded-control border border-line p-3">
            <h3 className="text-sm font-semibold text-ink">Your team: {myTeam.name}</h3>
            {myTeam.submission && (
              <p className="text-sm text-ink-muted">
                Handed in {formatDateTime(myTeam.submission.submittedAt)}
                {link && (<>{' · '}<a className={textLinkClasses} href={link} target="_blank" rel="noopener noreferrer">{myTeam.submission.link}</a></>)}
              </p>
            )}
            {hackathon.takingWork && (
              <>
                <form
                  className="grid gap-3"
                  onSubmit={(event) => {
                    event.preventDefault()
                    run(() => submitHackathonWork(hackathon.hackathonId, { title: work.title.trim(), summary: work.summary.trim(), link: work.link.trim() || null }),
                      'Work handed in. You can replace it until the end.', 'Couldn’t hand in the work.')
                  }}
                >
                  <TextField label="Submission title" value={work.title} maxLength={200} onChange={(event) => setWork({ ...work, title: event.target.value })} />
                  <TextAreaField label="What you built" rows={3} value={work.summary} maxLength={5000} onChange={(event) => setWork({ ...work, summary: event.target.value })} />
                  <TextField label="Link (optional)" hint="One https:// address." value={work.link} maxLength={500} onChange={(event) => setWork({ ...work, link: event.target.value })} />
                  <div><Button type="submit" size="sm">{myTeam.submission ? 'Replace submission' : 'Hand in'}</Button></div>
                </form>
                <Button size="sm" variant="secondary" onClick={() => run(() => leaveHackathonTeam(hackathon.hackathonId), 'You left the team.', 'Couldn’t leave the team.')}>
                  Leave team
                </Button>
              </>
            )}
          </div>
        )}
      </div>
    </Card>
  )
}

function ScoreForm({ item, run }: { item: JudgeItem; run: Run }) {
  const [score, setScore] = useState(item.myScore === null ? '' : String(item.myScore))
  const [comment, setComment] = useState(item.myComment ?? '')
  return (
    <form
      className="flex flex-wrap items-end gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        run(() => scoreSubmission(item.submissionId, Number(score), comment.trim() || null), 'Score saved.', 'Couldn’t save the score.')
      }}
    >
      <TextField label={`Score for ${item.teamName} (0 to 100)`} inputMode="numeric" value={score} onChange={(event) => setScore(event.target.value.replace(/\D/g, '').slice(0, 3))} />
      <TextField label={`Comment for ${item.teamName} (optional)`} value={comment} maxLength={1000} onChange={(event) => setComment(event.target.value)} />
      <Button type="submit" size="sm">Save score</Button>
    </form>
  )
}

function Judging({ run }: { run: Run }) {
  const judging = useApiQuery(queryKeys.hackathons.judging(), (signal) => getJudging(signal))
  if (judging.status !== 'loaded') {
    return <SectionState request={judging} what="judging" />
  }
  if (judging.data.length === 0) {
    return null
  }
  return (
    <Card title="Judging" description="Scores stay hidden from participants until the results are released.">
      <div className="space-y-4">
        {judging.data.map((hackathon) => (
          <section key={hackathon.hackathonId} aria-label={`Judging ${hackathon.title}`} className="space-y-2">
            <h3 className="text-sm font-semibold text-ink">{hackathon.title}</h3>
            {!hackathon.judging && hackathon.submissions.length === 0 && (
              <p className="text-sm text-ink-muted">Judging opens when the hackathon ends, {formatDateTime(hackathon.endsAt)}.</p>
            )}
            {hackathon.submissions.map((item) => (
              <div key={item.submissionId} className="space-y-2 rounded-control border border-line p-3">
                <p className="text-sm font-medium text-ink">{item.teamName}: {item.title}</p>
                <p className="text-sm break-words whitespace-pre-wrap text-ink">{item.summary}</p>
                {hackathon.judging ? <ScoreForm item={item} run={run} /> : item.myScore !== null && <p className="text-sm text-ink-muted">Your score: {item.myScore}</p>}
              </div>
            ))}
          </section>
        ))}
      </div>
    </Card>
  )
}

function Office({ run }: { run: Run }) {
  const all = useApiQuery(queryKeys.hackathons.office(), (signal) => getOfficeHackathons(signal))
  const [form, setForm] = useState({ title: '', description: '', startsAt: '', endsAt: '', maxTeamSize: '4' })
  const [judges, setJudges] = useState<Record<number, string>>({})
  return (
    <>
      <Card title="Add a hackathon">
        <form
          className="grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            const startsAt = localToIso(form.startsAt)
            const endsAt = localToIso(form.endsAt)
            run(async () => {
              if (startsAt === null || endsAt === null) {
                throw new Error('times')
              }
              await createHackathon({ title: form.title.trim(), description: form.description.trim() || null, startsAt, endsAt, maxTeamSize: Number(form.maxTeamSize) })
            }, 'Hackathon added as a draft.', 'Enter a title, a start, and an end after it.')
          }}
        >
          <TextField label="Hackathon title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
          <TextField label="Team size" inputMode="numeric" value={form.maxTeamSize} onChange={(event) => setForm({ ...form, maxTeamSize: event.target.value.replace(/\D/g, '') })} />
          <TextField label="Starts" type="datetime-local" value={form.startsAt} onChange={(event) => setForm({ ...form, startsAt: event.target.value })} />
          <TextField label="Ends" type="datetime-local" value={form.endsAt} onChange={(event) => setForm({ ...form, endsAt: event.target.value })} />
          <div className="sm:col-span-2">
            <TextAreaField label="Description (optional)" rows={2} value={form.description} maxLength={5000} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </div>
          <div><Button type="submit">Add hackathon</Button></div>
        </form>
      </Card>
      {all.status !== 'loaded' ? (
        <SectionState request={all} what="hackathons" />
      ) : all.data.length === 0 ? (
        <EmptyState title="No hackathons yet." />
      ) : (
        all.data.map((hackathon) => (
          <Card key={hackathon.hackathonId} title={hackathon.title} description={`${formatDateTime(hackathon.startsAt)} to ${formatDateTime(hackathon.endsAt)} · ${hackathon.teams} team(s)`}
            action={<Badge tone="neutral">{hackathon.status.replace('_', ' ').toLowerCase()}</Badge>}>
            <div className="space-y-3">
              <p className="text-sm text-ink-muted">Judges: {hackathon.judgeLoginIds.length === 0 ? 'none yet' : hackathon.judgeLoginIds.join(', ')}</p>
              <div className="flex flex-wrap items-end gap-2">
                {hackathon.status === 'DRAFT' && (
                  <Button size="sm" onClick={() => run(() => publishHackathon(hackathon.hackathonId), 'Hackathon published.', 'Couldn’t publish.')}>
                    Publish<span className="sr-only">{` ${hackathon.title}`}</span>
                  </Button>
                )}
                {hackathon.status === 'PUBLISHED' && (
                  <Button size="sm" variant="secondary" onClick={() => run(() => releaseHackathonResults(hackathon.hackathonId), 'Results released.', 'Couldn’t release the results.')}>
                    Release results<span className="sr-only">{` of ${hackathon.title}`}</span>
                  </Button>
                )}
              </div>
              {(hackathon.status === 'DRAFT' || hackathon.status === 'PUBLISHED') && (
                <form
                  className="flex flex-wrap items-end gap-2"
                  onSubmit={(event) => {
                    event.preventDefault()
                    run(() => addHackathonJudge(hackathon.hackathonId, (judges[hackathon.hackathonId] ?? '').trim()), 'Judge named.', 'Couldn’t name the judge.')
                  }}
                >
                  <TextField label={`Judge’s employee ID for ${hackathon.title}`} value={judges[hackathon.hackathonId] ?? ''} maxLength={100} onChange={(event) => setJudges({ ...judges, [hackathon.hackathonId]: event.target.value })} />
                  <Button type="submit" size="sm" variant="secondary">Name judge</Button>
                </form>
              )}
              {hackathon.standings.length > 0 && <Results title={hackathon.title} rows={hackathon.standings} />}
            </div>
          </Card>
        ))
      )}
    </>
  )
}

function Student({ run }: { run: Run }) {
  const hackathons = useApiQuery(queryKeys.hackathons.visible(), (signal) => getHackathons(signal))
  if (hackathons.status !== 'loaded') {
    return <SectionState request={hackathons} what="hackathons" />
  }
  if (hackathons.data.length === 0) {
    return <EmptyState title="No hackathons published yet." />
  }
  return <>{hackathons.data.map((hackathon) => <StudentHackathon key={hackathon.hackathonId} hackathon={hackathon} run={run} />)}</>
}

/** /hackathons: teams and submissions for students, judging for faculty judges, and running them for the campus office. */
export function HackathonsPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const queryClient = useQueryClient()
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const run: Run = (action, done, fallback) =>
    void action()
      .then(async () => {
        setMessage({ tone: 'success', text: done })
        await queryClient.invalidateQueries({ queryKey: queryKeys.hackathons.all })
      })
      .catch((caught: unknown) => setMessage({ tone: 'error', text: failureText(caught, fallback) ?? fallback }))
  return (
    <>
      <title>Hackathons · Stacc</title>
      <PageHeader title="Hackathons" description="Form a team, build something, and hand it in before the end. Results come after judging." />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {hasAnyRole(roles, ['STUDENT']) && <Student run={run} />}
        {hasAnyRole(roles, ['FACULTY']) && <Judging run={run} />}
        {hasAnyRole(roles, ['ADMIN']) && <Office run={run} />}
      </div>
    </>
  )
}
