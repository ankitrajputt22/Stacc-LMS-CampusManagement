export type NotificationCategory = 'COURSES' | 'CALENDAR' | 'SUPPORT' | 'APPROVALS' | 'CAMPUS'
export type EmailDelivery = 'OFF' | 'INSTANT' | 'DAILY'

export type NotificationChoice = { category: NotificationCategory; inApp: boolean; email: EmailDelivery }

export type NotificationSettings = { emailAvailable: boolean; choices: NotificationChoice[] }
