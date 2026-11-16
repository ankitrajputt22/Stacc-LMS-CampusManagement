import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsCommunicationApi'
import type { DiscussionThread } from '../types/communication'
import { DiscussionThreadView } from './Discussions'

vi.mock('../api/lmsCommunicationApi')

const thread: DiscussionThread = {
  threadId: 3,
  title: 'Exam syllabus?',
  body: 'Does unit 4 count?',
  authorLoginId: 'student1',
  authorKind: 'STUDENT',
  pinned: false,
  acceptedReplyId: null,
  createdAt: '2026-11-10T10:00:00Z',
  canAccept: true,
  canPin: false,
  replies: [
    { replyId: 8, authorLoginId: 'faculty1', authorKind: 'FACULTY', body: 'Yes, it does.', createdAt: '2026-11-10T11:00:00Z', accepted: false, hidden: false },
  ],
  locked: false,
  hidden: false,
  canModerate: false,
  canReport: true,
}

function renderThread() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <DiscussionThreadView side="student" lmsCourseId={5} threadId={3} basePath="/courses/5/discussions" />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DiscussionThreadView', () => {
  beforeEach(() => {
    vi.mocked(api.getDiscussion).mockResolvedValue(thread)
  })

  it('shows the question and replies as plain text, marking faculty and offering no pin to students', async () => {
    renderThread()
    expect(await screen.findByText('Does unit 4 count?')).toBeInTheDocument()
    expect(screen.getByText('Yes, it does.')).toBeInTheDocument()
    expect(screen.getByText('Faculty')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Pin/ })).not.toBeInTheDocument()
  })

  it('lets the asker accept a reply as the answer', async () => {
    vi.mocked(api.acceptReply).mockResolvedValue({ ...thread, acceptedReplyId: 8 })
    renderThread()
    await userEvent.click(await screen.findByRole('button', { name: /Accept as answer/ }))
    expect(api.acceptReply).toHaveBeenCalledWith('student', 5, 3, 8)
  })

  it('shows a hidden reply only as hidden and a locked discussion without the reply form', async () => {
    vi.mocked(api.getDiscussion).mockResolvedValue({
      ...thread,
      locked: true,
      replies: [{ replyId: 8, authorLoginId: null, authorKind: null, body: null, createdAt: '2026-11-10T11:00:00Z', accepted: false, hidden: true }],
    })
    renderThread()
    expect(await screen.findByText('This reply was hidden by the course’s faculty.')).toBeInTheDocument()
    expect(screen.getByText('This discussion is locked. It takes no new replies.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Accept as answer/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Post reply' })).not.toBeInTheDocument()
  })

  it('reports the discussion with a reason', async () => {
    vi.mocked(api.reportPost).mockResolvedValue(undefined)
    renderThread()
    await userEvent.click(await screen.findByRole('button', { name: /^Report\s*this discussion$/ }))
    await userEvent.type(screen.getByLabelText('Why are you reporting this discussion?'), 'Off topic')
    await userEvent.click(screen.getByRole('button', { name: 'Send report' }))
    expect(api.reportPost).toHaveBeenCalledWith(5, 3, null, 'Off topic')
  })
})
