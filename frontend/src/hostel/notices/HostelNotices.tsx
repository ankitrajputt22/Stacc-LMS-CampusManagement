import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../../api/apiError'
import { addHostelNotice, archiveHostelNotice, getManagedHostelNotices, getMyHostelNotices, publishHostelNotice } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { EmptyState } from '../../components/states/EmptyState'
import { formatDateTime } from '../../courses/dates'
import { SectionState } from '../../courses/SectionState'
import type { HostelNotice } from '../../types/hostel'
import { Alert, Badge, Button, Card, SelectField, TextAreaField, TextField } from '../../ui'

function refusal(failure: unknown, what: string): string {
  const status = getHttpStatus(failure)
  const message = getApiError(failure)?.message ?? null
  return message && status !== null && status < 500 ? message : `Couldn’t ${what}. Try again.`
}

function NoticeText({ notice }: { notice: HostelNotice }) {
  return (
    <div className="space-y-1">
      <div className="flex flex-wrap items-center gap-2">
        <h3 className="font-semibold text-ink">{notice.title}</h3>
        {notice.emergency && <Badge tone="danger">Emergency</Badge>}
        {notice.block && <Badge tone="neutral">{`Block ${notice.block}`}</Badge>}
      </div>
      <p className="text-sm leading-6 whitespace-pre-line text-ink">{notice.message}</p>
      {notice.publishedAt && (
        <p className="text-xs text-ink-muted">
          Published <time dateTime={notice.publishedAt}>{formatDateTime(notice.publishedAt)}</time>
        </p>
      )}
    </div>
  )
}

/**
 * Notices of the hostels a manager may use (every hostel for the hostel office, their own for a warden): add a draft
 * for the hostel or a block, publish (an emergency tells every resident it reaches), and archive, each confirmed.
 */
export function HostelNoticeManager({ hostels }: { hostels: { hostelId: number; name: string }[] }) {
  const queryClient = useQueryClient()
  const [hostelId, setHostelId] = useState(hostels[0].hostelId)
  const notices = useApiQuery(queryKeys.hostel.notices(hostelId), (signal) => getManagedHostelNotices(hostelId, signal))
  const [block, setBlock] = useState('')
  const [title, setTitle] = useState('')
  const [text, setText] = useState('')
  const [emergency, setEmergency] = useState(false)
  const [confirming, setConfirming] = useState<{ id: number; kind: 'publish' | 'archive' } | null>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function done(note: string) {
    setConfirming(null)
    setMessage({ tone: 'success', text: note })
    await queryClient.invalidateQueries({ queryKey: ['hostel', 'notices'] })
  }

  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (title.trim() === '' || text.trim() === '') {
      setMessage({ tone: 'error', text: 'Enter a title and the message.' })
      return
    }
    try {
      await addHostelNotice({ hostelId, block: block.trim() || null, title: title.trim(), message: text.trim(), emergency })
      setTitle('')
      setText('')
      setBlock('')
      setEmergency(false)
      await done('Notice added as a draft.')
    } catch (failure) {
      setMessage({ tone: 'error', text: refusal(failure, 'add the notice') })
    }
  }

  return (
    <Card title="Hostel notices" description="Notices for a hostel or one block. An emergency notice is also sent to every resident it reaches." padding="none">
      <p role="status" className="sr-only">
        {message?.tone === 'success' ? message.text : ''}
      </p>
      <form className="grid gap-3 border-b border-line px-4 py-4 sm:grid-cols-2 sm:px-5" onSubmit={(event) => void add(event)} noValidate>
        <SelectField label="Hostel" value={String(hostelId)} onChange={(event) => setHostelId(Number(event.target.value))}>
          {hostels.map((hostel) => (
            <option key={hostel.hostelId} value={hostel.hostelId}>
              {hostel.name}
            </option>
          ))}
        </SelectField>
        <TextField label="Block (optional)" hint="Empty for the whole hostel." value={block} maxLength={50} onChange={(event) => setBlock(event.target.value)} />
        <TextField label="Notice title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
        <label className="flex items-center gap-2 self-end text-sm text-ink">
          <input type="checkbox" checked={emergency} onChange={(event) => setEmergency(event.target.checked)} />
          Emergency
        </label>
        <div className="sm:col-span-2">
          <TextAreaField label="Notice message" rows={3} maxLength={5000} value={text} onChange={(event) => setText(event.target.value)} />
        </div>
        <div>
          <Button type="submit">Add notice</Button>
        </div>
      </form>
      {message && (
        <div className="px-4 pt-4 sm:px-5">
          <Alert tone={message.tone}>{message.text}</Alert>
        </div>
      )}
      {notices.status !== 'loaded' ? (
        <SectionState request={notices} what="notices" />
      ) : notices.data.length === 0 ? (
        <EmptyState title="No notices for this hostel yet." />
      ) : (
        <ul className="divide-y divide-line">
          {notices.data.map((notice) => (
            <li key={notice.noticeId} className="space-y-3 px-4 py-4 sm:px-5">
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone={notice.status === 'PUBLISHED' ? 'success' : notice.status === 'ARCHIVED' ? 'neutral' : 'warning'}>
                  {notice.status === 'PUBLISHED' ? 'Published' : notice.status === 'ARCHIVED' ? 'Archived' : 'Draft'}
                </Badge>
              </div>
              <NoticeText notice={notice} />
              {confirming?.id === notice.noticeId ? (
                <StatusConfirmation
                  question={confirming.kind === 'publish' ? `Publish “${notice.title}”?` : `Archive “${notice.title}”?`}
                  detail={
                    confirming.kind === 'publish'
                      ? notice.emergency
                        ? 'Residents it reaches see it now, and each of them is told in their Inbox.'
                        : 'Residents it reaches see it on their Hostel page.'
                      : 'It is kept but no longer shown or changed.'
                  }
                  confirmLabel={confirming.kind === 'publish' ? 'Publish' : 'Archive'}
                  busyLabel="Saving…"
                  describeRefusal={(status, apiMessage) => (apiMessage && status !== null && status < 500 ? apiMessage : 'Couldn’t save. Try again.')}
                  onCancel={() => setConfirming(null)}
                  onConfirm={async () => {
                    if (confirming.kind === 'publish') {
                      await publishHostelNotice(notice.noticeId)
                      await done('Notice published.')
                    } else {
                      await archiveHostelNotice(notice.noticeId)
                      await done('Notice archived.')
                    }
                  }}
                />
              ) : (
                notice.status !== 'ARCHIVED' && (
                  <div className="flex flex-wrap gap-2">
                    {notice.status === 'DRAFT' && (
                      <Button size="sm" onClick={() => setConfirming({ id: notice.noticeId, kind: 'publish' })}>
                        Publish<span className="sr-only">{` ${notice.title}`}</span>
                      </Button>
                    )}
                    <Button size="sm" variant="secondary" onClick={() => setConfirming({ id: notice.noticeId, kind: 'archive' })}>
                      Archive<span className="sr-only">{` ${notice.title}`}</span>
                    </Button>
                  </div>
                )
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** The published notices of the resident's current hostel and block, emergencies first. Nothing when there are none. */
export function ResidentHostelNotices() {
  const notices = useApiQuery(queryKeys.hostel.myNotices(), (signal) => getMyHostelNotices(signal))
  if (notices.status !== 'loaded' || notices.data.length === 0) {
    return null
  }
  return (
    <Card title="Hostel notices" padding="none">
      <ul className="divide-y divide-line">
        {notices.data.map((notice) => (
          <li key={notice.noticeId} className="px-4 py-4 sm:px-5">
            <NoticeText notice={notice} />
          </li>
        ))}
      </ul>
    </Card>
  )
}
