export type NotificationCategory = 'COURSES' | 'CALENDAR' | 'SUPPORT' | 'APPROVALS' | 'CAMPUS'
export type EmailDelivery = 'OFF' | 'INSTANT' | 'DAILY'

export type NotificationChoice = { category: NotificationCategory; inApp: boolean; email: EmailDelivery }

/** Every category's choice, and whether the college has email switched on at all. */
export type NotificationSettings = { emailAvailable: boolean; choices: NotificationChoice[] }
