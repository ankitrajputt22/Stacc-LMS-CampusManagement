import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { getManagedMess, getMyMess, rateMeal, setMessChange, setMessTemplate } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import type { DayMenu, ManagerMess, Meal } from '../../types/hostel'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../../ui'
import { canRate, dayLabel, MEALS, mealLabel, WEEKDAYS } from './messText'

function refusal(failure: unknown, what: string): string {
  const status = getHttpStatus(failure)
  const message = getApiError(failure)?.message ?? null
  return message && status !== null && status < 500 ? message : `Couldn’t ${what}. Try again.`
}

function todayText(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function Week({ week }: { week: DayMenu[] }) {
  return (
    <ul className="divide-y divide-line">
      {week.map((day) => (
        <li key={day.date} className="px-4 py-3 sm:px-5">
          <p className="text-sm font-semibold text-ink">{dayLabel(day.date)}</p>
          {day.meals.length === 0 ? (
            <p className="text-sm text-ink-muted">No menu set.</p>
          ) : (
            <dl className="mt-1 grid gap-x-4 gap-y-1 text-sm sm:grid-cols-[7rem_1fr]">
              {day.meals.map((line) => (
                <div key={line.meal} className="contents">
                  <dt className="text-ink-muted">{mealLabel(line.meal)}</dt>
                  <dd className="text-ink">
                    {line.items}
                    {line.changed && (
                      <span className="ml-2">
                        <Badge tone="info">{line.note ?? 'Changed for this day'}</Badge>
                      </span>
                    )}
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </li>
      ))}
    </ul>
  )
}

/** Rate one meal: 1 to 5 and an optional comment, once. */
function RateForm({ date, meal, onRated }: { date: string; meal: Meal; onRated: () => Promise<void> }) {
  const [rating, setRating] = useState('')
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (rating === '') {
      setError('Choose a rating.')
      return
    }
    try {
      await rateMeal(date, meal, Number(rating), comment.trim() || null)
      await onRated()
    } catch (failure) {
      setError(refusal(failure, 'save your rating'))
    }
  }

  return (
    <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => void submit(event)} noValidate>
      <SelectField label={`Rating for ${mealLabel(meal).toLowerCase()} on ${dayLabel(date)}`} value={rating} onChange={(event) => setRating(event.target.value)}>
        <option value="">Choose</option>
        {[5, 4, 3, 2, 1].map((value) => (
          <option key={value} value={value}>
            {`${value} of 5`}
          </option>
        ))}
      </SelectField>
      <TextField label="Comment (optional)" value={comment} maxLength={500} onChange={(event) => setComment(event.target.value)} />
      <Button type="submit" size="sm">
        Rate
      </Button>
      {error && <p className="w-full text-sm text-danger">{error}</p>}
    </form>
  )
}

/** A resident's mess: this week's menu of their hostel, and today's meals to rate. Nothing without a hostel bed. */
export function ResidentMessCard() {
  const queryClient = useQueryClient()
  const mess = useApiQuery(queryKeys.hostel.myMess(), (signal) => getMyMess(signal))
  const [message, setMessage] = useState('')
  if (mess.status === 'loaded' && mess.data.hostelName === null) {
    return null
  }
  if (mess.status !== 'loaded') {
    return (
      <Card title="Mess">
        <SectionState request={mess} what="the mess menu" />
      </Card>
    )
  }
  const today = todayText()
  const todays = mess.data.week.find((day) => day.date === today)
  const rated = new Set(mess.data.rated.map((given) => `${given.date}:${given.meal}`))
  const toRate = (todays?.meals ?? []).filter((line) => canRate(today, today) && !rated.has(`${today}:${line.meal}`))
  return (
    <Card title="Mess" description={`${mess.data.hostelName}: this week’s menu. Changes for a single day are marked.`} padding="none">
      <p role="status" className="sr-only">
        {message}
      </p>
      {toRate.length > 0 && (
        <div className="space-y-3 border-b border-line px-4 py-4 sm:px-5">
          <p className="text-sm font-semibold text-ink">Rate today’s meals</p>
          {toRate.map((line) => (
            <RateForm
              key={line.meal}
              date={today}
              meal={line.meal}
              onRated={async () => {
                setMessage(`Thanks for rating ${mealLabel(line.meal).toLowerCase()}.`)
                await queryClient.invalidateQueries({ queryKey: queryKeys.hostel.myMess() })
              }}
            />
          ))}
        </div>
      )}
      <Week week={mess.data.week} />
    </Card>
  )
}

function TemplateEditor({ data, onSaved }: { data: ManagerMess; onSaved: (message: string) => Promise<void> }) {
  const [lines, setLines] = useState<Record<string, string>>(() =>
    Object.fromEntries(data.template.map((line) => [`${line.dayOfWeek}:${line.meal}`, line.items])),
  )
  const [error, setError] = useState<string | null>(null)

  async function save() {
    const filled = Object.entries(lines)
      .filter(([, items]) => items.trim() !== '')
      .map(([key, items]) => {
        const [day, meal] = key.split(':')
        return { dayOfWeek: Number(day), meal: meal as Meal, items: items.trim() }
      })
    if (filled.length === 0) {
      setError('Enter at least one meal.')
      return
    }
    try {
      await setMessTemplate(data.hostelId, filled)
      setError(null)
      await onSaved('Weekly menu saved.')
    } catch (failure) {
      setError(refusal(failure, 'save the weekly menu'))
    }
  }

  return (
    <div className="space-y-3 px-4 py-4 sm:px-5">
      <p className="text-sm font-semibold text-ink">Weekly menu</p>
      {WEEKDAYS.map((weekday, index) => (
        <fieldset key={weekday} className="grid gap-2 sm:grid-cols-4">
          <legend className="text-sm font-medium text-ink sm:col-span-4">{weekday}</legend>
          {MEALS.map((meal) => (
            <TextField
              key={meal.value}
              label={`${weekday} ${meal.label.toLowerCase()}`}
              value={lines[`${index + 1}:${meal.value}`] ?? ''}
              maxLength={500}
              onChange={(event) => setLines((current) => ({ ...current, [`${index + 1}:${meal.value}`]: event.target.value }))}
            />
          ))}
        </fieldset>
      ))}
      {error && <Alert tone="error">{error}</Alert>}
      <Button onClick={() => void save()}>Save weekly menu</Button>
    </div>
  )
}

function DayChangeForm({ hostelId, onSaved }: { hostelId: number; onSaved: (message: string) => Promise<void> }) {
  const [date, setDate] = useState(todayText)
  const [meal, setMeal] = useState<Meal>('LUNCH')
  const [items, setItems] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (items.trim() === '') {
      setError('Enter what is served.')
      return
    }
    try {
      await setMessChange(hostelId, date, meal, items.trim(), note.trim() || null)
      setItems('')
      setNote('')
      setError(null)
      await onSaved('Change for the day saved.')
    } catch (failure) {
      setError(refusal(failure, 'save the change'))
    }
  }

  return (
    <form className="grid gap-3 px-4 py-4 sm:grid-cols-4 sm:items-end sm:px-5" onSubmit={(event) => void submit(event)} noValidate>
      <p className="text-sm font-semibold text-ink sm:col-span-4">Change one meal on one day</p>
      <TextField label="Day" type="date" value={date} onChange={(event) => setDate(event.target.value)} />
      <SelectField label="Meal" value={meal} onChange={(event) => setMeal(event.target.value as Meal)}>
        {MEALS.map((choice) => (
          <option key={choice.value} value={choice.value}>
            {choice.label}
          </option>
        ))}
      </SelectField>
      <TextField label="Served instead" value={items} maxLength={500} onChange={(event) => setItems(event.target.value)} />
      <TextField label="Note (optional)" hint="For example Festival." value={note} maxLength={200} onChange={(event) => setNote(event.target.value)} />
      {error && <div className="sm:col-span-4"><Alert tone="error">{error}</Alert></div>}
      <div>
        <Button type="submit">Save change</Button>
      </div>
    </form>
  )
}

/**
 * The mess of the hostels a manager may use: the weekly menu, single-day changes, the coming week, and the last
 * month's feedback without names (an average only for at least five ratings).
 */
export function MessManager({ hostels }: { hostels: { hostelId: number; name: string }[] }) {
  const queryClient = useQueryClient()
  const [hostelId, setHostelId] = useState(hostels[0].hostelId)
  const mess = useApiQuery(queryKeys.hostel.mess(hostelId), (signal) => getManagedMess(hostelId, signal))
  const [message, setMessage] = useState('')

  async function saved(text: string) {
    setMessage(text)
    await queryClient.invalidateQueries({ queryKey: ['hostel', 'mess'] })
  }

  return (
    <Card title="Mess" description="The weekly menu, changes for single days, and what residents think." padding="none">
      <p role="status" className="sr-only">
        {message}
      </p>
      <div className="border-b border-line px-4 py-4 sm:px-5">
        <SelectField label="Hostel for the mess" value={String(hostelId)} onChange={(event) => setHostelId(Number(event.target.value))}>
          {hostels.map((hostel) => (
            <option key={hostel.hostelId} value={hostel.hostelId}>
              {hostel.name}
            </option>
          ))}
        </SelectField>
        {message && <p className="mt-2 text-sm text-success">{message}</p>}
      </div>
      {mess.status !== 'loaded' ? (
        <SectionState request={mess} what="the mess" />
      ) : (
        <div className="divide-y divide-line">
          <div className="px-4 py-4 sm:px-5">
            <p className="text-sm font-semibold text-ink">Last 30 days</p>
            <ul className="mt-1 grid gap-1 text-sm sm:grid-cols-4">
              {mess.data.lastMonth.map((summary) => (
                <li key={summary.meal}>
                  {`${mealLabel(summary.meal)}: ${summary.ratings} ${summary.ratings === 1 ? 'rating' : 'ratings'}`}
                  {summary.average !== null ? `, average ${summary.average} of 5` : summary.ratings > 0 ? ' (average shown from 5 ratings)' : ''}
                </li>
              ))}
            </ul>
            {mess.data.comments.length > 0 && (
              <ul className="mt-2 space-y-1 text-sm text-ink-muted">
                {mess.data.comments.map((comment, index) => (
                  <li key={index}>{`${dayLabel(comment.date)}, ${mealLabel(comment.meal).toLowerCase()}, ${comment.rating} of 5: ${comment.comment}`}</li>
                ))}
              </ul>
            )}
          </div>
          <DayChangeForm hostelId={hostelId} onSaved={saved} />
          {mess.data.week.every((day) => day.meals.length === 0) ? (
            <EmptyState title="No menu for the coming week yet." description="Fill in the weekly menu below." />
          ) : (
            <Week week={mess.data.week} />
          )}
          <TemplateEditor key={`${hostelId}-${mess.data.template.length}`} data={mess.data} onSaved={saved} />
        </div>
      )}
    </Card>
  )
}
