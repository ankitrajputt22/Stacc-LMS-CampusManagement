import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { endPairing, getAllPairings, pairMentor } from '../api/mentoringApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { StatePanel } from '../components/states/StatePanel'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import { Alert, Badge, Button, Card, PageHeader, TextField } from '../ui'

/** /admin/mentoring: who mentors whom. Meetings and notes stay with each pair. */
export function AdminMentoringPage() {
  const { account } = useAuth()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const queryClient = useQueryClient()
  const pairings = useApiQuery(isAdmin ? queryKeys.mentoring.admin() : null, (signal) => getAllPairings(signal))
  const [form, setForm] = useState({ mentor: '', mentee: '' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const run = (action: () => Promise<unknown>, done: string, fallback: string) =>
    void action()
      .then(async () => {
        setMessage({ tone: 'success', text: done })
        await queryClient.invalidateQueries({ queryKey: queryKeys.mentoring.all })
      })
      .catch((caught: unknown) => setMessage({ tone: 'error', text: failureText(caught, fallback) ?? '' }))
  return (
    <>
      <title>Mentoring · Stacc</title>
      <PageHeader title="Mentoring" description="Pair faculty mentors with students. Meetings and notes stay with each pair." />
      <div className="mt-6 space-y-6">
        {!isAdmin ? (
          <StatePanel><AccessDeniedState title="Mentoring pairings are managed by admin accounts." /></StatePanel>
        ) : (
          <>
            <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
            {message && <Alert tone={message.tone}>{message.text}</Alert>}
            <Card title="Pair a mentor with a student">
              <form
                className="grid gap-3 sm:grid-cols-3"
                onSubmit={(event) => {
                  event.preventDefault()
                  run(() => pairMentor(form.mentor.trim(), form.mentee.trim()).then(() => setForm({ mentor: '', mentee: '' })), 'Paired.', 'Couldn’t pair them.')
                }}
              >
                <TextField label="Mentor’s employee ID" value={form.mentor} maxLength={100} onChange={(event) => setForm({ ...form, mentor: event.target.value })} />
                <TextField label="Student’s college ID" value={form.mentee} maxLength={100} onChange={(event) => setForm({ ...form, mentee: event.target.value })} />
                <div className="self-end"><Button type="submit">Pair</Button></div>
              </form>
            </Card>
            <Card title="Pairings" padding="none">
              {pairings.status !== 'loaded' ? (
                <SectionState request={pairings} what="pairings" />
              ) : pairings.data.length === 0 ? (
                <EmptyState title="No pairings yet." />
              ) : (
                <ul className="divide-y divide-line text-sm">
                  {pairings.data.map((pairing) => (
                    <li key={pairing.assignmentId} className="flex flex-wrap items-center gap-2 px-4 py-3 sm:px-5">
                      <span className="font-mono text-ink">{pairing.mentorLoginId}</span>
                      <span className="text-ink-muted">mentors</span>
                      <span className="font-mono text-ink">{pairing.menteeLoginId}</span>
                      {pairing.active ? (
                        <Button size="sm" variant="secondary" onClick={() => run(() => endPairing(pairing.assignmentId), 'Pairing ended.', 'Couldn’t end the pairing.')}>
                          End<span className="sr-only">{` ${pairing.mentorLoginId} mentoring ${pairing.menteeLoginId}`}</span>
                        </Button>
                      ) : (
                        <Badge tone="neutral">Ended</Badge>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          </>
        )}
      </div>
    </>
  )
}
