export type NotificationType =
  | 'ASSIGNMENT_PUBLISHED'
  | 'ASSIGNMENT_GRADED'
  | 'QUIZ_PUBLISHED'
  | 'ANNOUNCEMENT_PUBLISHED'
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
  | 'HOSTEL_EMERGENCY'
  | 'CAMPUS_NOTICE_PUBLISHED'
  | 'CAMPUS_SERVICE_READY'
  | 'CAMPUS_SERVICE_COMPLETED'

export type NotificationSourceType =
  | 'ASSIGNMENT'
  | 'SUBMISSION_GRADE'
  | 'QUIZ'
  | 'ACADEMIC_CALENDAR_EVENT'
  | 'ATTENDANCE_RECORD'
  | 'COURSE_RESULT'
  | 'HELPDESK_TICKET'
  | 'APPROVAL_REQUEST'
  | 'HOSTEL_ALLOCATION'
  | 'CAMPUS_NOTICE'
  | 'CAMPUS_SERVICE_REQUEST'

export type NotificationInboxItem = {
  notificationRecipientId: number
  notificationId: number
  type: NotificationType
  title: string
  message: string
  sourceType: NotificationSourceType | null
  sourceId: number | null
  actionPath: string | null
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
