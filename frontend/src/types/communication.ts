/** Course discussions and messages, as the API returns them. People appear by login ID only. */
export type AuthorKind = 'STUDENT' | 'FACULTY'

/** Which side of a course the signed-in account uses: its student pages or its teaching pages. */
export type CourseSide = 'student' | 'faculty'

export type DiscussionThreadSummary = {
  threadId: number
  title: string
  authorLoginId: string
  authorKind: AuthorKind
  pinned: boolean
  answered: boolean
  replyCount: number
  createdAt: string
  lastActivityAt: string
}

export type DiscussionReply = {
  replyId: number
  authorLoginId: string
  authorKind: AuthorKind
  body: string
  createdAt: string
  accepted: boolean
}

export type DiscussionThread = {
  threadId: number
  title: string
  body: string
  authorLoginId: string
  authorKind: AuthorKind
  pinned: boolean
  acceptedReplyId: number | null
  createdAt: string
  canAccept: boolean
  canPin: boolean
  replies: DiscussionReply[]
}

export type ConversationSummary = {
  conversationId: number
  subject: string
  studentLoginId: string
  createdAt: string
  lastMessageAt: string
}

export type MessageAttachment = {
  attachmentId: number
  fileName: string
  contentType: string
  sizeBytes: number
}

export type ConversationMessage = {
  messageId: number
  authorLoginId: string
  authorKind: AuthorKind
  body: string
  createdAt: string
  attachments: MessageAttachment[]
}

export type Conversation = {
  conversationId: number
  subject: string
  studentLoginId: string
  createdAt: string
  messages: ConversationMessage[]
}
