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
import { Alert, Button, Card, DataTable, PageHeader, TextField } from '../ui'
import { formatMomentDay } from '../erp/requestWords'
import type { MentoringPairing } from '../types/mentoring'

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
      <PageHeader title="Mentoring" description="Pair faculty mentors with students. Meetings, notes, and action items stay with each pair; admins never read them." />
      <div className="mt-6 space-y-6">
        {!isAdmin ? (
          <StatePanel><AccessDeniedState title="Mentoring pairings are managed by admin accounts." /></StatePanel>
        ) : (
          <>
            <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
            {message && <Alert tone={message.tone}>{message.text}</Alert>}
            <Card title="Mentoring pairings" padding="none">
              {pairings.status !== 'loaded' ? (
                <SectionState request={pairings} what="pairings" />
              ) : pairings.data.length === 0 ? (
                <EmptyState title="No pairings yet." />
              ) : (
                <DataTable<MentoringPairing>
                  caption="Mentoring pairings"
                  rows={pairings.data}
                  rowKey={(pairing) => pairing.assignmentId}
                  columns={[
                    { key: 'mentor', header: 'Mentor (employee ID)', primary: true, cell: (pairing) => pairing.mentorLoginId },
                    { key: 'mentee', header: 'Student (college ID)', cell: (pairing) => pairing.menteeLoginId },
                    { key: 'status', header: 'Status', cell: (pairing) => <span className="font-semibold">{pairing.active ? 'Active' : 'Ended'}</span> },
                    {
                      key: 'period',
                      header: 'Period',
                      cell: (pairing) => (
                        <>
                          <time dateTime={pairing.startedAt}>{formatMomentDay(pairing.startedAt)}</time>
                          {' – '}
                          {pairing.endedAt && <time dateTime={pairing.endedAt}>{formatMomentDay(pairing.endedAt)}</time>}
                        </>
                      ),
                    },
                    {
                      key: 'action',
                      header: 'Action',
                      cell: (pairing) =>
                        pairing.active ? (
                          <Button size="sm" onClick={() => run(() => endPairing(pairing.assignmentId), 'Pairing ended.', 'Couldn’t end the pairing.')}>
                            End<span className="sr-only">{` ${pairing.mentorLoginId} mentoring ${pairing.menteeLoginId}`}</span>
                          </Button>
                        ) : (
                          <span className="text-ink-muted">Kept as history</span>
                        ),
                    },
                  ]}
                />
              )}
            </Card>
            <Card title="Pair a mentor with a student">
              <form
                className="grid gap-3 sm:grid-cols-2"
                onSubmit={(event) => {
                  event.preventDefault()
                  run(() => pairMentor(form.mentor.trim(), form.mentee.trim()).then(() => setForm({ mentor: '', mentee: '' })), 'Paired.', 'Couldn’t pair them.')
                }}
              >
                <TextField label="Mentor’s employee ID" value={form.mentor} maxLength={100} onChange={(event) => setForm({ ...form, mentor: event.target.value })} />
                <TextField label="Student’s college ID" value={form.mentee} maxLength={100} onChange={(event) => setForm({ ...form, mentee: event.target.value })} />
                <div><Button type="submit">Pair</Button></div>
              </form>
            </Card>
          </>
        )}
      </div>
    </>
  )
}
