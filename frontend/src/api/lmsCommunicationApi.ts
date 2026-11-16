import type {
  Conversation,
  ConversationSummary,
  CourseSide,
  DiscussionReport,
  DiscussionThread,
  DiscussionThreadSummary,
} from '../types/communication'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

// The signed-in account is the one in the session; the backend checks course access on every request.

function base(side: CourseSide, lmsCourseId: number): string {
  return side === 'faculty' ? `/lms/faculty/courses/${lmsCourseId}` : `/lms/courses/${lmsCourseId}`
}

export async function getDiscussions(side: CourseSide, lmsCourseId: number, signal?: AbortSignal): Promise<DiscussionThreadSummary[]> {
  return (await apiClient.get<DiscussionThreadSummary[]>(`${base(side, lmsCourseId)}/discussions`, { signal })).data
}

export async function startDiscussion(side: CourseSide, lmsCourseId: number, title: string, body: string): Promise<DiscussionThread> {
  return (await apiClient.post<DiscussionThread>(`${base(side, lmsCourseId)}/discussions`, { title, body })).data
}

export async function getDiscussion(side: CourseSide, lmsCourseId: number, threadId: number, signal?: AbortSignal): Promise<DiscussionThread> {
  return (await apiClient.get<DiscussionThread>(`${base(side, lmsCourseId)}/discussions/${threadId}`, { signal })).data
}

export async function replyToDiscussion(side: CourseSide, lmsCourseId: number, threadId: number, body: string): Promise<DiscussionThread> {
  return (await apiClient.post<DiscussionThread>(`${base(side, lmsCourseId)}/discussions/${threadId}/replies`, { body })).data
}

export async function acceptReply(side: CourseSide, lmsCourseId: number, threadId: number, replyId: number): Promise<DiscussionThread> {
  return (await apiClient.post<DiscussionThread>(`${base(side, lmsCourseId)}/discussions/${threadId}/replies/${replyId}/accept`)).data
}

export async function pinDiscussion(lmsCourseId: number, threadId: number, pinned: boolean): Promise<DiscussionThread> {
  return (await apiClient.post<DiscussionThread>(`${base('faculty', lmsCourseId)}/discussions/${threadId}/pin`, { pinned })).data
}

export async function getConversations(side: CourseSide, lmsCourseId: number, signal?: AbortSignal): Promise<ConversationSummary[]> {
  return (await apiClient.get<ConversationSummary[]>(`${base(side, lmsCourseId)}/conversations`, { signal })).data
}

/** A student starts a conversation with the course's faculty. */
export async function startConversation(lmsCourseId: number, subject: string, body: string, files: File[] = []): Promise<Conversation> {
  const form = messageForm(body, files)
  form.append('subject', subject)
  return (await apiClient.post<Conversation>(`${base('student', lmsCourseId)}/conversations`, form, { timeout: FILE_TIMEOUT_MS })).data
}

export async function getConversation(side: CourseSide, lmsCourseId: number, conversationId: number, signal?: AbortSignal): Promise<Conversation> {
  return (await apiClient.get<Conversation>(`${base(side, lmsCourseId)}/conversations/${conversationId}`, { signal })).data
}

export async function sendMessage(
  side: CourseSide,
  lmsCourseId: number,
  conversationId: number,
  body: string,
  files: File[] = [],
): Promise<Conversation> {
  const url = `${base(side, lmsCourseId)}/conversations/${conversationId}/messages`
  return (await apiClient.post<Conversation>(url, messageForm(body, files), { timeout: FILE_TIMEOUT_MS })).data
}

/** Downloads a file of a conversation through the API, never from storage directly. */
export async function downloadMessageAttachment(
  side: CourseSide,
  lmsCourseId: number,
  conversationId: number,
  attachmentId: number,
): Promise<Blob> {
  const url = `${base(side, lmsCourseId)}/conversations/${conversationId}/attachments/${attachmentId}`
  return (await apiClient.get<Blob>(url, { responseType: 'blob', timeout: FILE_TIMEOUT_MS })).data
}

function messageForm(body: string, files: File[]): FormData {
  const form = new FormData()
  form.append('body', body)
  for (const file of files) {
    form.append('files', file)
  }
  return form
}

/** Hides a thread from students, or shows it again (faculty). */
export async function hideDiscussion(lmsCourseId: number, threadId: number, hidden: boolean): Promise<DiscussionThread> {
  return (await apiClient.post<DiscussionThread>(`${base('faculty', lmsCourseId)}/discussions/${threadId}/hide`, { hidden })).data
}

/** Locks a thread against new replies, or opens it again (faculty). */
export async function lockDiscussion(lmsCourseId: number, threadId: number, locked: boolean): Promise<DiscussionThread> {
  return (await apiClient.post<DiscussionThread>(`${base('faculty', lmsCourseId)}/discussions/${threadId}/lock`, { locked })).data
}

export async function hideReply(lmsCourseId: number, threadId: number, replyId: number, hidden: boolean): Promise<DiscussionThread> {
  return (await apiClient.post<DiscussionThread>(`${base('faculty', lmsCourseId)}/discussions/${threadId}/replies/${replyId}/hide`, { hidden }))
    .data
}

/** Reports the thread (no reply) or one of its replies to the course's faculty (students). */
export async function reportPost(lmsCourseId: number, threadId: number, replyId: number | null, reason: string): Promise<void> {
  await apiClient.post(`${base('student', lmsCourseId)}/discussions/${threadId}/reports`, { replyId, reason })
}

export async function getDiscussionReports(lmsCourseId: number, signal?: AbortSignal): Promise<DiscussionReport[]> {
  return (await apiClient.get<DiscussionReport[]>(`${base('faculty', lmsCourseId)}/discussion-reports`, { signal })).data
}

/** Hides the reported post, or dismisses its open reports. */
export async function resolveReport(lmsCourseId: number, reportId: number, hide: boolean): Promise<void> {
  await apiClient.post(`${base('faculty', lmsCourseId)}/discussion-reports/${reportId}/resolve`, { hide })
}
