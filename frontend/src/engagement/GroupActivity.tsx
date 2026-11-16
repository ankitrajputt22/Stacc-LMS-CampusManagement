import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { addGroupSession, cancelGroupSession, getGroupActivity, postGroupAnnouncement } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { GroupSession } from '../types/engagement'
import { Alert, Badge, Button, SelectField, TextAreaField, TextField } from '../ui'

function refusal(failure: unknown, what: string): string {
  const status = getHttpStatus(failure)
  const message = getApiError(failure)?.message ?? null
  return message && status !== null && status < 500 ? message : `Couldn’t ${what}. Try again.`
}

/**
 * A group's meetings, practices, and announcements, opened on demand for its members and officers. Officers (and the
 * campus office) schedule and cancel sessions, which appear on members' calendars, and post announcements, which
 * reach members' Inboxes.
 */
export function GroupActivityPanel({ groupId, groupName }: { groupId: number; groupName: string }) {
  const [open, setOpen] = useState(false)
  if (!open) {
    return (
      <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>
        Sessions and announcements<span className="sr-only">{` of ${groupName}`}</span>
      </Button>
    )
  }
  return <Activity groupId={groupId} groupName={groupName} />
}

function Activity({ groupId, groupName }: { groupId: number; groupName: string }) {
  const queryClient = useQueryClient()
  const activity = useApiQuery(queryKeys.engagement.activity(groupId), (signal) => getGroupActivity(groupId, signal))
  const [kind, setKind] = useState<GroupSession['kind']>('MEETING')
  const [title, setTitle] = useState('')
  const [location, setLocation] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [postTitle, setPostTitle] = useState('')
  const [postText, setPostText] = useState('')
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function done(text: string) {
    setMessage({ tone: 'success', text })
    await queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all })
  }

  async function schedule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (title.trim() === '' || startsAt === '' || endsAt === '') {
      setMessage({ tone: 'error', text: 'Enter a title, a start, and an end.' })
      return
    }
    try {
      await addGroupSession(groupId, {
        kind,
        title: title.trim(),
        location: location.trim() || null,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
      })
      setTitle('')
      setLocation('')
      await done('Session scheduled. It is on members’ calendars.')
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, 'schedule the session') })
    }
  }

  async function announce(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (postTitle.trim() === '' || postText.trim() === '') {
      setMessage({ tone: 'error', text: 'Enter a title and the message.' })
      return
    }
    try {
      await postGroupAnnouncement(groupId, postTitle.trim(), postText.trim())
      setPostTitle('')
      setPostText('')
      await done('Announcement posted. Members are told in their Inbox.')
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, 'post the announcement') })
    }
  }

  if (activity.status !== 'loaded') {
    return <SectionState request={activity} what="sessions and announcements" />
  }
  const data = activity.data
  return (
    <section aria-label={`Sessions and announcements of ${groupName}`} className="space-y-4 rounded-control border border-line p-3">
      <p role="status" className="sr-only">
        {message?.tone === 'success' ? message.text : ''}
      </p>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <div>
        <h4 className="text-sm font-semibold text-ink">Sessions</h4>
        {data.sessions.length === 0 ? (
          <p className="text-sm text-ink-muted">No sessions coming up.</p>
        ) : (
          <ul className="mt-1 space-y-1.5 text-sm">
            {data.sessions.map((session) => (
              <li key={session.sessionId} className="flex flex-wrap items-center gap-2">
                <span className={session.status === 'CANCELLED' ? 'text-ink-muted line-through' : 'text-ink'}>
                  {`${session.kind === 'PRACTICE' ? 'Practice' : 'Meeting'}: ${session.title} · ${formatDateTime(session.startsAt)}`}
                  {session.location && ` · ${session.location}`}
                </span>
                {session.status === 'CANCELLED' && <Badge tone="neutral">Cancelled</Badge>}
                {data.canManage && session.status === 'SCHEDULED' && (
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() =>
                      void cancelGroupSession(session.sessionId)
                        .then(() => done('Session cancelled.'))
                        .catch((failure: unknown) => setMessage({ tone: 'error', text: refusal(failure, 'cancel the session') }))
                    }
                  >
                    Cancel<span className="sr-only">{` ${session.title}`}</span>
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      {data.canManage && (
        <form className="grid gap-2 sm:grid-cols-2" onSubmit={(event) => void schedule(event)} noValidate>
          <SelectField label="Session type" value={kind} onChange={(event) => setKind(event.target.value as GroupSession['kind'])}>
            <option value="MEETING">Meeting</option>
            <option value="PRACTICE">Practice</option>
          </SelectField>
          <TextField label="Session title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
          <TextField label="Starts" type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} />
          <TextField label="Ends" type="datetime-local" value={endsAt} onChange={(event) => setEndsAt(event.target.value)} />
          <TextField label="Location (optional)" value={location} maxLength={200} onChange={(event) => setLocation(event.target.value)} />
          <div className="self-end">
            <Button type="submit" size="sm">
              Schedule session
            </Button>
          </div>
        </form>
      )}
      <div>
        <h4 className="text-sm font-semibold text-ink">Announcements</h4>
        {data.announcements.length === 0 ? (
          <p className="text-sm text-ink-muted">No announcements yet.</p>
        ) : (
          <ul className="mt-1 space-y-2">
            {data.announcements.map((announcement) => (
              <li key={announcement.announcementId}>
                <p className="text-sm font-medium text-ink">{announcement.title}</p>
                <p className="text-sm whitespace-pre-line text-ink">{announcement.message}</p>
                <p className="text-xs text-ink-muted">{formatDateTime(announcement.createdAt)}</p>
              </li>
            ))}
          </ul>
        )}
      </div>
      {data.canManage && (
        <form className="space-y-2" onSubmit={(event) => void announce(event)} noValidate>
          <TextField label="Announcement title" value={postTitle} maxLength={200} onChange={(event) => setPostTitle(event.target.value)} />
          <TextAreaField label="Announcement message" rows={3} maxLength={5000} value={postText} onChange={(event) => setPostText(event.target.value)} />
          <Button type="submit" size="sm">
            Post announcement
          </Button>
        </form>
      )}
    </section>
  )
}
