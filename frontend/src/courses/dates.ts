// The browser's own language and time zone, so a time sent in UTC reads as local time.
const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** A time from the API, such as 2026-10-14T09:30:00Z, as people read it, such as "14 Oct 2026, 3:00 pm". */
export function formatDateTime(isoTime: string): string {
  const time = new Date(isoTime)
  return Number.isNaN(time.getTime()) ? isoTime : dateTimeFormat.format(time)
}

/**
 * Whether a deadline from the API has passed. This is for display only: it says nothing about any
 * student's work.
 */
export function isPast(isoTime: string, now: number = Date.now()): boolean {
  const time = new Date(isoTime).getTime()
  return !Number.isNaN(time) && time < now
}

const LOCAL_INPUT_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/**
 * A time from the API as the value of a datetime-local input, in the browser's own time zone, such as
 * 2026-10-20T23:59 for 2026-10-20T18:29:00Z in India.
 */
export function toLocalInputValue(isoTime: string): string {
  const time = new Date(isoTime)
  if (Number.isNaN(time.getTime())) {
    return ''
  }
  return `${time.getFullYear()}-${pad(time.getMonth() + 1)}-${pad(time.getDate())}T${pad(time.getHours())}:${pad(time.getMinutes())}`
}

/**
 * The value of a datetime-local input, read as the browser's local time, as the absolute moment it means.
 * Its toISOString() is what the API is sent. Null when it is not a complete, real date and time.
 */
export function fromLocalInputValue(value: string): Date | null {
  const parts = LOCAL_INPUT_PATTERN.exec(value)
  if (parts === null) {
    return null
  }
  const [year, month, day, hours, minutes, seconds] = parts.slice(1).map((part) => Number(part ?? 0))
  const time = new Date(year, month - 1, day, hours, minutes, seconds)
  // Months and days out of range roll over in Date; such a value was not a real date.
  return Number.isNaN(time.getTime()) || time.getMonth() !== month - 1 || time.getDate() !== day ? null : time
}

/** The browser's time zone, such as "Asia/Kolkata", to say which time a deadline is entered in. */
export function localTimeZoneName(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}
