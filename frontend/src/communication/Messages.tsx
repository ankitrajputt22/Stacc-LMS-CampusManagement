import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import {
  downloadMessageAttachment,
  getConversation,
  getConversations,
  sendMessage,
  startConversation,
} from '../api/lmsCommunicationApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { useFileDownload } from '../courses/useFileDownload'
import type { CourseSide } from '../types/communication'
import { Badge, Button, ButtonLink, Card } from '../ui'
import { PostForm, PostText } from './PostForm'

/**
 * Private conversations with the course's faculty. Students see their own and start new ones; faculty see every
 * student's conversation in the course and answer them.
 */
export function ConversationList({ side, lmsCourseId, basePath }: { side: CourseSide; lmsCourseId: number; basePath: string }) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const conversations = useApiQuery(queryKeys.lms.conversations(side, lmsCourseId), (signal) =>
    getConversations(side, lmsCourseId, signal),
  )
  const [starting, setStarting] = useState(false)
  const student = side === 'student'

  return (
    <div className="space-y-6">
      {starting && (
        <Card title="New message" description="Only you and the course’s faculty can read this conversation.">
          <PostForm
            titleLabel="Subject"
            bodyLabel="Message"
            submitLabel="Send message"
            allowFiles
            onCancel={() => setStarting(false)}
            onSubmit={async (subject, body, files) => {
              const conversation = await startConversation(lmsCourseId, subject, body, files)
              await queryClient.invalidateQueries({ queryKey: queryKeys.lms.conversations(side, lmsCourseId) })
              void navigate(`${basePath}/${conversation.conversationId}`)
            }}
          />
        </Card>
      )}
      <Card
        title={student ? 'Messages with your faculty' : 'Messages from students'}
        padding="none"
        action={student && !starting ? <Button size="sm" onClick={() => setStarting(true)}>New message</Button> : undefined}
      >
        {conversations.status !== 'loaded' ? (
          <SectionState request={conversations} what="messages" />
        ) : conversations.data.length === 0 ? (
          <EmptyState
            title="No messages yet."
            description={student ? 'Send the course’s faculty a private message.' : 'Students of this course haven’t written yet.'}
          />
        ) : (
          <ul className="divide-y divide-line">
            {conversations.data.map((conversation) => (
              <li key={conversation.conversationId} className="px-4 py-3 sm:px-5">
                <Link
                  to={`${basePath}/${conversation.conversationId}`}
                  className="font-semibold text-stacc-primary underline-offset-4 hover:underline"
                >
                  {conversation.subject}
                </Link>
                <p className="mt-0.5 text-sm text-ink-muted">
                  {student ? '' : `${conversation.studentLoginId} · `}Last message{' '}
                  <time dateTime={conversation.lastMessageAt}>{formatDateTime(conversation.lastMessageAt)}</time>
                </p>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}

export function ConversationView({
  side,
  lmsCourseId,
  conversationId,
  basePath,
}: {
  side: CourseSide
  lmsCourseId: number
  conversationId: number
  basePath: string
}) {
  const queryClient = useQueryClient()
  const key = queryKeys.lms.conversation(side, lmsCourseId, conversationId)
  const conversation = useApiQuery(key, (signal) => getConversation(side, lmsCourseId, conversationId, signal))
  const [announcement, setAnnouncement] = useState('')
  const downloads = useFileDownload(
    useCallback((fileId: number) => downloadMessageAttachment(side, lmsCourseId, conversationId, fileId), [side, lmsCourseId, conversationId]),
  )
  const back = (
    <ButtonLink to={basePath} variant="secondary" size="sm">
      All messages
    </ButtonLink>
  )

  if (conversation.status !== 'loaded') {
    return (
      <Card title="Conversation" action={back}>
        {conversation.status === 'notFound' ? (
          <EmptyState title="Conversation unavailable" description="This conversation could not be found or is not yours." />
        ) : (
          <SectionState request={conversation} what="the conversation" />
        )}
      </Card>
    )
  }
  const data = conversation.data

  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">
        {announcement}
      </p>
      <Card title={data.subject} action={back} padding="none">
        {side === 'faculty' && <p className="px-4 pt-3 text-sm text-ink-muted sm:px-5">With {data.studentLoginId}</p>}
        <ol className="divide-y divide-line">
          {data.messages.map((message) => (
            <li key={message.messageId} className="space-y-2 px-4 py-4 sm:px-5">
              <p className="flex flex-wrap items-center gap-2 text-sm text-ink-muted">
                <span className="font-medium text-ink">{message.authorLoginId}</span>
                {message.authorKind === 'FACULTY' && <Badge tone="brand">Faculty</Badge>}
                <time dateTime={message.createdAt}>{formatDateTime(message.createdAt)}</time>
              </p>
              <PostText text={message.body} />
              {message.attachments.length > 0 && (
                <ul aria-label="Files" className="flex flex-wrap gap-2">
                  {message.attachments.map((file) => (
                    <li key={file.attachmentId}>
                      <Button
                        variant="secondary"
                        size="sm"
                        disabled={downloads.downloadingId === file.attachmentId}
                        onClick={() => void downloads.download({ id: file.attachmentId, originalFileName: file.fileName })}
                      >
                        <span className="break-all">Download {file.fileName}</span>
                      </Button>
                      {downloads.failure?.fileId === file.attachmentId && (
                        <p className="mt-1 text-sm text-danger">{downloads.failure.message}</p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ol>
      </Card>
      <Card title="Reply">
        <PostForm
          bodyLabel="Your message"
          submitLabel="Send"
          allowFiles
          onSubmit={async (_title, body, files) => {
            await sendMessage(side, lmsCourseId, conversationId, body, files)
            setAnnouncement('Message sent.')
            await queryClient.invalidateQueries({ queryKey: queryKeys.lms.conversations(side, lmsCourseId) })
            await queryClient.invalidateQueries({ queryKey: key })
          }}
        />
      </Card>
    </div>
  )
}
