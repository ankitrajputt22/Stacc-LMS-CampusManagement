import { type FormEvent, useId, useState } from 'react'
import { addStudyPlan, planStudy } from '../api/aiApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import type { AiStudyPlanDay } from '../types/ai'
import { Button, SelectField } from '../ui'
import { AiMessage } from './AiMessage'
import { hintClasses, panelClasses } from './aiStyles'

function dayName(date: string): string {
  return new Date(`${date}T12:00:00`).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
}

/**
 * Study planner (v0.15.7): a plan for the next 7 or 14 days, scheduled by code around assignment deadlines, the student's
 * own mastery and flashcards, and busy days in their calendar. Nothing is stored unless the student adds it to their
 * calendar or To-Do list.
 */
export function StudyPlanPanel({ lmsCourseId }: { lmsCourseId: number }) {
  const id = useId()
  const [days, setDays] = useState('7')
  const [minutes, setMinutes] = useState('60')
  const [plan, setPlan] = useState<AiStudyPlanDay[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  function refusal(failure: unknown, fallback: string) {
    const status = getHttpStatus(failure)
    if (status === 401) {
      return
    }
    const said = getApiError(failure)?.message
    setMessage(status === 403 ? 'You don’t have access to this.' : said && status !== null && status < 500 ? said : fallback)
  }

  async function make(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      setPlan(await planStudy(lmsCourseId, Number(days), Number(minutes)))
    } catch (failure) {
      refusal(failure, 'Couldn’t make a plan. Try again.')
    } finally {
      setBusy(false)
    }
  }

  async function add(to: 'calendar' | 'todo') {
    setBusy(true)
    setMessage(null)
    try {
      const added = await addStudyPlan(lmsCourseId, to, Number(days), Number(minutes))
      setMessage(to === 'calendar'
        ? `Added ${added} study ${added === 1 ? 'day' : 'days'} to your calendar.`
        : `Added ${added} study ${added === 1 ? 'session' : 'sessions'} to your To-Do list.`)
    } catch (failure) {
      refusal(failure, 'Couldn’t add the plan. Try again.')
    } finally {
      setBusy(false)
    }
  }

  const planned = plan?.filter((day) => day.sessions.length > 0) ?? []
  return (
    <section aria-labelledby={`${id}-heading`} className={panelClasses}>
      <h2 id={`${id}-heading`} className="text-base font-semibold text-ink">Study plan</h2>
      <p className={hintClasses}>
        Planned around your deadlines, your practice, and the busy days in your own calendar. Nothing is saved unless you add it.
      </p>
      <form noValidate onSubmit={make} className="mt-3 grid gap-3 sm:grid-cols-3 sm:items-end">
        <SelectField label="Plan for" value={days} onChange={(event) => setDays(event.target.value)}>
          <option value="7">The next 7 days</option>
          <option value="14">The next 14 days</option>
        </SelectField>
        <SelectField label="Time a day" value={minutes} onChange={(event) => setMinutes(event.target.value)}>
          {['30', '45', '60', '90', '120', '180'].map((value) => <option key={value} value={value}>{value} minutes</option>)}
        </SelectField>
        <Button type="submit" disabled={busy}>Make a plan</Button>
      </form>
      <div aria-live="polite" className="mt-3 empty:hidden">
        {message && <p className="text-sm text-ink-muted">{message}</p>}
      </div>
      {plan && (planned.length === 0 ? (
        <AiMessage tone="note" className="mt-3">Nothing to plan for these days.</AiMessage>
      ) : (
        <>
          <ol className="mt-3 divide-y divide-line text-sm">
            {planned.map((day) => (
              <li key={day.date} className="py-2">
                <p className="font-medium text-ink">
                  {dayName(day.date)} · {day.planned} min{day.busy && ' (a busy day in your calendar)'}
                </p>
                <ul className="mt-1 list-disc pl-5 text-ink">
                  {day.sessions.map((session) => <li key={`${session.kind}-${session.targetId}-${session.title}`}>{session.title} · {session.minutes} min</li>)}
                </ul>
              </li>
            ))}
          </ol>
          <div className="mt-3 flex flex-wrap gap-2">
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => void add('calendar')}>Add to my calendar</Button>
            <Button size="sm" variant="secondary" disabled={busy} onClick={() => void add('todo')}>Add to my To-Do</Button>
          </div>
        </>
      ))}
    </section>
  )
}
