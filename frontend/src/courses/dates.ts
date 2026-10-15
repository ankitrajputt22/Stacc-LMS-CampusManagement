// The browser's own language and time zone, so a time sent in UTC reads as local time.
const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

/** A time from the API, such as 2026-10-14T09:30:00Z, as people read it, such as "14 Oct 2026, 3:00 pm". */
export function formatDateTime(isoTime: string): string {
  const time = new Date(isoTime)
  return Number.isNaN(time.getTime()) ? isoTime : dateTimeFormat.format(time)
}
