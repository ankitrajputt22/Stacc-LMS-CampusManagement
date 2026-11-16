import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  acceptReply,
  getDiscussion,
  getDiscussionReports,
  getDiscussions,
  hideDiscussion,
  hideReply,
  lockDiscussion,
  pinDiscussion,
  replyToDiscussion,
  reportPost,
  resolveReport,
  startDiscussion,
} from '../api/lmsCommunicationApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import type { AuthorKind, CourseSide } from '../types/communication'
import { Alert, Badge, Button, ButtonLink, Card, TextAreaField } from '../ui'
import { PostForm, PostText } from './PostForm'
import { refusalText } from './refusal'

function Author({ loginId, kind }: { loginId: string; kind: AuthorKind }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-1.5">
      <span className="font-medium text-ink">{loginId}</span>
      {kind === 'FACULTY' && <Badge tone="brand">Faculty</Badge>}
    </span>
  )
}

/** Report a post: a reason, then Send report or Cancel. The faculty see the report without the reporter. */
function ReportForm({ what, onSend, onCancel }: { what: string; onSend: (reason: string) => Promise<void>; onCancel: () => void }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (reason.trim() === '') {
      setError('Say why you are reporting it.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onSend(reason.trim())
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t send the report. Try again.'))
      setBusy(false)
    }
  }

  return (
    <form className="space-y-3 rounded-md border border-line p-3" onSubmit={(event) => void submit(event)} noValidate>
      <TextAreaField
        label={`Why are you reporting ${what}?`}
        hint="Only the course’s faculty see reports, without your name."
        rows={3}
        maxLength={500}
        value={reason}
        error={error}
        onChange={(event) => setReason(event.target.value)}
      />
      <div className="flex flex-wrap gap-2">
        <Button type="submit" size="sm" disabled={busy}>
          {busy ? 'Sending…' : 'Send report'}
        </Button>
        <Button type="button" variant="secondary" size="sm" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

/** The course's open reports for its faculty: each with Hide post and Dismiss, which resolve every report of that post. */
function ReportsCard({ lmsCourseId, basePath }: { lmsCourseId: number; basePath: string }) {
  const queryClient = useQueryClient()
  const reports = useApiQuery(queryKeys.lms.discussionReports(lmsCourseId), (signal) => getDiscussionReports(lmsCourseId, signal))
  const [error, setError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')

  async function resolve(reportId: number, hide: boolean) {
    setError(null)
    try {
      await resolveReport(lmsCourseId, reportId, hide)
      setAnnouncement(hide ? 'Hid the post.' : 'Dismissed the report.')
      await queryClient.invalidateQueries({
        queryKey: queryKeys.lms.discussions('faculty', lmsCourseId),
      })
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t resolve the report. Try again.'))
    }
  }

  if (reports.status === 'loaded' && reports.data.length === 0) {
    return null
  }
  return (
    <Card title="Reported posts" description="Students reported these posts. Hide a post or dismiss its reports." padding="none">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      {error && (
        <div className="px-4 pt-4 sm:px-5">
          <Alert tone="error">{error}</Alert>
        </div>
      )}
      {reports.status !== 'loaded' ? (
        <SectionState request={reports} what="reports" />
      ) : (
        <ul className="divide-y divide-line">
          {reports.data.map((report) => (
            <li key={report.reportId} className="space-y-2 px-4 py-4 sm:px-5">
              <p className="text-sm">
                <Link to={`${basePath}/${report.threadId}`} className="font-semibold text-stacc-primary underline-offset-4 hover:underline">
                  {report.threadTitle}
                </Link>
                <span className="text-ink-muted">{` · ${report.replyId === null ? 'the discussion' : 'a reply'} by ${report.postAuthorLoginId}`}</span>
              </p>
              <PostText text={report.excerpt} />
              <p className="text-sm text-ink">
                <span className="font-medium">Reason: </span>
                {report.reason}
              </p>
              <div className="flex flex-wrap gap-2">
                {!report.postHidden && (
                  <Button size="sm" onClick={() => void resolve(report.reportId, true)}>
                    Hide post
                    <span className="sr-only">{` reported in ${report.threadTitle}`}</span>
                  </Button>
                )}
                <Button variant="secondary" size="sm" onClick={() => void resolve(report.reportId, false)}>
                  Dismiss
                  <span className="sr-only">{` the report in ${report.threadTitle}`}</span>
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/** The course's discussions: pinned first, then the most recently active, with Start a discussion. */
export function DiscussionBoard({ side, lmsCourseId, basePath }: { side: CourseSide; lmsCourseId: number; basePath: string }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const threads = useApiQuery(queryKeys.lms.discussions(side, lmsCourseId), (signal) => getDiscussions(side, lmsCourseId, signal))
  const [starting, setStarting] = useState(false)

  return (
    <div className="space-y-6">
      {side === 'faculty' && <ReportsCard lmsCourseId={lmsCourseId} basePath={basePath} />}
      {starting && (
        <Card title="Start a discussion" description="Everyone in the course can read and reply. Posts can’t be edited or deleted.">
          <PostForm
            titleLabel="Title"
            bodyLabel="Question or topic"
            submitLabel="Post discussion"
            onCancel={() => setStarting(false)}
            onSubmit={async (title, body) => {
              const thread = await startDiscussion(side, lmsCourseId, title, body)
              await queryClient.invalidateQueries({
                queryKey: queryKeys.lms.discussions(side, lmsCourseId),
              })
              void navigate(`${basePath}/${thread.threadId}`)
            }}
          />
        </Card>
      )}
      <Card
        title="Discussions"
        padding="none"
        action={starting ? undefined : <Button size="sm" onClick={() => setStarting(true)}>Start a discussion</Button>}
      >
        {threads.status !== 'loaded' ? (
          <SectionState request={threads} what="discussions" />
        ) : threads.data.length === 0 ? (
          <EmptyState title="No discussions yet." description="Start one to ask the class or the faculty a question." />
        ) : (
          <ul className="divide-y divide-line">
            {threads.data.map((thread) => (
              <li key={thread.threadId} className="px-4 py-3 sm:px-5">
                <div className="flex flex-wrap items-center gap-2">
                  <Link to={`${basePath}/${thread.threadId}`} className="font-semibold text-stacc-primary underline-offset-4 hover:underline">
                    {thread.title}
                  </Link>
                  {thread.pinned && <Badge tone="info">Pinned</Badge>}
                  {thread.answered && <Badge tone="success">Answered</Badge>}
                  {thread.locked && <Badge tone="neutral">Locked</Badge>}
                  {thread.hidden && <Badge tone="warning">Hidden from students</Badge>}
                </div>
                <p className="mt-0.5 text-sm text-ink-muted">
                  {thread.authorLoginId} · {thread.replyCount === 1 ? '1 reply' : `${thread.replyCount} replies`} · Last activity{' '}
                  <time dateTime={thread.lastActivityAt}>{formatDateTime(thread.lastActivityAt)}</time>
                </p>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

/** One discussion with its replies, Reply, Accept as answer (asker or faculty), and Pin (faculty). */
export function DiscussionThreadView({
  side,
  lmsCourseId,
  threadId,
  basePath,
}: {
  side: CourseSide
  lmsCourseId: number
  threadId: number
  basePath: string
}) {
  const queryClient = useQueryClient()
  const key = queryKeys.lms.discussion(side, lmsCourseId, threadId)
  const thread = useApiQuery(key, (signal) => getDiscussion(side, lmsCourseId, threadId, signal))
  const [error, setError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')
  // 0 reports the thread itself; a reply ID reports that reply.
  const [reporting, setReporting] = useState<number | null>(null)

  async function changed(message: string) {
    setAnnouncement(message)
    await queryClient.invalidateQueries({
      queryKey: queryKeys.lms.discussions(side, lmsCourseId),
    })
  }

  async function act(action: () => Promise<unknown>, message: string) {
    setError(null)
    try {
      await action()
      await changed(message)
    } catch (failure) {
      setError(refusalText(failure, 'Couldn’t save. Try again.'))
    }
  }

  const back = (
    <ButtonLink to={basePath} variant="secondary" size="sm">
      All discussions
    </ButtonLink>
  )

  if (thread.status !== 'loaded') {
    return (
      <Card title="Discussion" action={back}>
        {thread.status === 'notFound' ? (
          <EmptyState title="Discussion unavailable" description="This discussion could not be found in this course." />
        ) : (
          <SectionState request={thread} what="the discussion" />
        )}
      </Card>
    )
  }
  const data = thread.data

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card title={data.title} action={back}>
        <div className="space-y-3">
          <p className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
            <Author loginId={data.authorLoginId} kind={data.authorKind} />
            <time dateTime={data.createdAt}>{formatDateTime(data.createdAt)}</time>
            {data.pinned && <Badge tone="info">Pinned</Badge>}
            {data.locked && <Badge tone="neutral">Locked</Badge>}
            {data.hidden && <Badge tone="warning">Hidden from students</Badge>}
          </p>
          <PostText text={data.body} />
          <div className="flex flex-wrap gap-2">
            {data.canPin && (
              <Button variant="secondary" size="sm" onClick={() => void act(() => pinDiscussion(lmsCourseId, threadId, !data.pinned), data.pinned ? 'Unpinned.' : 'Pinned.')}>
                {data.pinned ? 'Unpin' : 'Pin to top'}
              </Button>
            )}
            {data.canModerate && (
              <>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => void act(() => lockDiscussion(lmsCourseId, threadId, !data.locked), data.locked ? 'Opened for replies.' : 'Locked.')}
                >
                  {data.locked ? 'Unlock' : 'Lock'}
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() =>
                    void act(() => hideDiscussion(lmsCourseId, threadId, !data.hidden), data.hidden ? 'Shown to students again.' : 'Hidden from students.')
                  }
                >
                  {data.hidden ? 'Show to students' : 'Hide from students'}
                </Button>
              </>
            )}
            {data.canReport && reporting !== 0 && (
              <Button variant="secondary" size="sm" onClick={() => setReporting(0)}>
                Report<span className="sr-only"> this discussion</span>
              </Button>
            )}
          </div>
          {reporting === 0 && (
            <ReportForm
              what="this discussion"
              onCancel={() => setReporting(null)}
              onSend={async (reason) => {
                await reportPost(lmsCourseId, threadId, null, reason)
                setReporting(null)
                setAnnouncement('Report sent to the course’s faculty.')
              }}
            />
          )}
          {error && <Alert tone="error">{error}</Alert>}
        </div>
      </Card>
      <Card title={data.replies.length === 1 ? '1 reply' : `${data.replies.length} replies`} padding="none">
        {data.replies.length === 0 ? (
          <EmptyState title="No replies yet." description="Be the first to reply." />
        ) : (
          <ol className="divide-y divide-line">
            {data.replies.map((reply) => (
              <li key={reply.replyId} className={`space-y-2 px-4 py-4 sm:px-5 ${reply.accepted ? 'bg-surface-muted' : ''}`}>
                {reply.body === null || reply.authorLoginId === null || reply.authorKind === null ? (
                  <p className="text-sm italic text-ink-muted">This reply was hidden by the course’s faculty.</p>
                ) : (
                  <>
                    <p className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
                      <Author loginId={reply.authorLoginId} kind={reply.authorKind} />
                      <time dateTime={reply.createdAt}>{formatDateTime(reply.createdAt)}</time>
                      {reply.accepted && <Badge tone="success">Accepted answer</Badge>}
                      {reply.hidden && <Badge tone="warning">Hidden from students</Badge>}
                    </p>
                    <PostText text={reply.body} />
                  </>
                )}
                <div className="flex flex-wrap gap-2">
                  {data.canModerate && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() =>
                        void act(
                          () => hideReply(lmsCourseId, threadId, reply.replyId, !reply.hidden),
                          reply.hidden ? 'Reply shown to students again.' : 'Reply hidden from students.',
                        )
                      }
                    >
                      {reply.hidden ? 'Show reply' : 'Hide reply'}
                      <span className="sr-only">{` from ${reply.authorLoginId ?? 'a student'}`}</span>
                    </Button>
                  )}
                  {data.canReport && !reply.hidden && reporting !== reply.replyId && (
                    <Button variant="secondary" size="sm" onClick={() => setReporting(reply.replyId)}>
                      Report
                      <span className="sr-only">{` the reply from ${reply.authorLoginId ?? 'a student'}`}</span>
                    </Button>
                  )}
                </div>
                {reporting === reply.replyId && (
                  <ReportForm
                    what="this reply"
                    onCancel={() => setReporting(null)}
                    onSend={async (reason) => {
                      await reportPost(lmsCourseId, threadId, reply.replyId, reason)
                      setReporting(null)
                      setAnnouncement('Report sent to the course’s faculty.')
                    }}
                  />
                )}
                {data.canAccept && !reply.accepted && !reply.hidden && (
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => void act(() => acceptReply(side, lmsCourseId, threadId, reply.replyId), 'Accepted the answer.')}
                  >
                    Accept as answer
                    <span className="sr-only">{` from ${reply.authorLoginId}`}</span>
                  </Button>
                )}
              </li>
            ))}
          </ol>
        )}
      </Card>
      <Card title="Reply">
        {data.locked ? (
          <p className="text-sm text-ink-muted">This discussion is locked. It takes no new replies.</p>
        ) : (
          <PostForm
            bodyLabel="Your reply"
            submitLabel="Post reply"
            onSubmit={async (_title, body) => {
              await replyToDiscussion(side, lmsCourseId, threadId, body)
              await queryClient.invalidateQueries({ queryKey: key })
              await changed('Posted your reply.')
            }}
          />
        )}
      </Card>
    </div>
  )
}
