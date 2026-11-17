import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { getNotificationSettings, saveNotificationSettings } from '../api/notificationsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { SectionState } from '../courses/SectionState'
import type { EmailDelivery, NotificationCategory, NotificationChoice, NotificationSettings } from '../types/notificationSettings'
import { Alert, Button, Card, PageHeader, SelectField } from '../ui'

const CATEGORIES: Record<NotificationCategory, { label: string; detail: string }> = {
  COURSES: { label: 'Courses', detail: 'New assignments and quizzes, grades, attendance changes, and results.' },
  CALENDAR: { label: 'Calendar', detail: 'Official academic calendar events published or cancelled.' },
  SUPPORT: { label: 'Support', detail: 'Replies and changes on your support tickets.' },
  APPROVALS: { label: 'Approvals', detail: 'Requests waiting for your decision, and decisions on your requests.' },
  CAMPUS: { label: 'Campus', detail: 'Hostel allocations, campus notices, and completed service requests.' },
}

const EMAIL: Record<EmailDelivery, string> = { OFF: 'No email', INSTANT: 'Email each one', DAILY: 'Daily digest' }

function SettingsForm({ settings }: { settings: NotificationSettings }) {
  const queryClient = useQueryClient()
  const [choices, setChoices] = useState<NotificationChoice[]>(settings.choices)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

  function change(category: NotificationCategory, update: Partial<NotificationChoice>) {
    setChoices((current) => current.map((choice) => (choice.category === category ? { ...choice, ...update } : choice)))
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      const saved = await saveNotificationSettings(choices)
      queryClient.setQueryData(queryKeys.me.notificationSettings(), saved)
      setMessage({ tone: 'success', text: 'Saved your notification settings.' })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const apiMessage = getApiError(failure)?.message
        setMessage({ tone: 'error', text: apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Try again.' })
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={(event) => void save(event)} className="space-y-4">
      {!settings.emailAvailable && (
        <Alert tone="info">Email is not switched on for the college yet. Your email choices are kept and used once it is.</Alert>
      )}
      <Card title="What you get">
        <div aria-hidden="true" className="hidden border-b border-line pb-2 text-base font-bold text-ink sm:grid sm:grid-cols-[minmax(0,1fr)_8rem_14rem] sm:gap-4">
          <span />
          <span className="text-center">In the Inbox</span>
          <span>Email</span>
        </div>
        <ul className="divide-y divide-line border-b border-line">
          {choices.map((choice) => (
            <li key={choice.category} className="grid gap-3 px-1 py-4 sm:grid-cols-[minmax(0,1fr)_8rem_14rem] sm:items-center sm:gap-4 sm:px-3">
              <div className="min-w-0">
                <p className="text-lg font-bold text-ink">{CATEGORIES[choice.category].label}</p>
                <p className="text-[0.95rem] text-ink">{CATEGORIES[choice.category].detail}</p>
              </div>
              <label className="inline-flex items-center gap-2 text-sm text-ink sm:justify-center">
                <input
                  type="checkbox"
                  checked={choice.inApp}
                  onChange={(event) => change(choice.category, { inApp: event.target.checked })}
                  className="size-5 accent-stacc-primary"
                />
                <span className="sm:sr-only">In the Inbox</span>
                <span className="sr-only"> for {CATEGORIES[choice.category].label}</span>
              </label>
              <SelectField
                label={<span className="sm:sr-only">{`Email for ${CATEGORIES[choice.category].label}`}</span>}
                value={choice.email}
                onChange={(event) => change(choice.category, { email: event.target.value as EmailDelivery })}
              >
                {(Object.keys(EMAIL) as EmailDelivery[]).map((value) => (
                  <option key={value} value={value}>
                    {EMAIL[value]}
                  </option>
                ))}
              </SelectField>
            </li>
          ))}
        </ul>
        <p className="mt-4 text-[0.95rem] text-ink">Emails go to the address the college recorded for you.</p>
        {message && (
          <div className="mt-3">
            <Alert tone={message.tone}>{message.text}</Alert>
          </div>
        )}
        <div className="mt-4">
          <Button type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Save settings'}
          </Button>
        </div>
      </Card>
    </form>
  )
}

/** /inbox/settings: the person's own choices of what reaches the Inbox and email, per category. */
export function NotificationSettingsPage() {
  const settings = useApiQuery(queryKeys.me.notificationSettings(), (signal) => getNotificationSettings(signal))
  return (
    <div className="max-w-5xl">
      <title>Notification settings · Stacc</title>
      <PageHeader
        title="Notification settings"
        breadcrumb={<Breadcrumb parent={{ to: '/inbox', label: 'Inbox' }} current="Notification settings" />}
        description="Choose what shows in your Inbox and what is emailed to you."
      />
      <div className="mt-6">
        {settings.status === 'loaded' ? <SettingsForm settings={settings.data} /> : <SectionState request={settings} what="your settings" />}
      </div>
    </div>
  )
}
