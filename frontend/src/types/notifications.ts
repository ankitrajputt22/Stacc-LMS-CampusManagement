// The Inbox exactly as the backend sends it. Notifications are created by the backend only.

export type NotificationType =
  | 'ASSIGNMENT_PUBLISHED'
  | 'ASSIGNMENT_GRADED'
  | 'ACADEMIC_CALENDAR_PUBLISHED'
  | 'ACADEMIC_CALENDAR_CANCELLED'
  | 'ATTENDANCE_UPDATED'
  | 'RESULT_PUBLISHED'
  | 'RESULT_UPDATED'
  | 'HELPDESK_REPLY'
  | 'HELPDESK_STATUS_CHANGED'
  | 'HELPDESK_ASSIGNED'
  | 'APPROVAL_ACTION_REQUIRED'
  | 'APPROVAL_APPROVED'
  | 'APPROVAL_REJECTED'
  | 'APPROVAL_CANCELLED'
  | 'HOSTEL_ALLOCATED'
  | 'HOSTEL_VACATED'

export type NotificationSourceType =
  | 'ASSIGNMENT'
  | 'SUBMISSION_GRADE'
  | 'ACADEMIC_CALENDAR_EVENT'
  | 'ATTENDANCE_RECORD'
  | 'COURSE_RESULT'
  | 'HELPDESK_TICKET'
  | 'APPROVAL_REQUEST'
  | 'HOSTEL_ALLOCATION'

/** One notification in the signed-in account's own Inbox. */
export type NotificationInboxItem = {
  /** The Inbox row, used to mark it read or unread. */
  notificationRecipientId: number
  notificationId: number
  type: NotificationType
  title: string
  /** Plain text. */
  message: string
  sourceType: NotificationSourceType | null
  sourceId: number | null
  /** A path inside the app, such as /calendar, or null. The page there checks access as always. */
  actionPath: string | null
  /** When it was marked read, or null while unread. */
  readAt: string | null
  createdAt: string
}

export type NotificationPage = {
  items: NotificationInboxItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type UnreadNotificationCount = { unreadCount: number }

export type NotificationReadAllResult = { markedRead: number; unreadCount: number }
