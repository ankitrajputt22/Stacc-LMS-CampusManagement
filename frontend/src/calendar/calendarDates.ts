// Calendar months and days in the browser's own time zone. Official events are plain dates such as 2026-10-20 and are
// never read with new Date('2026-10-20'), which would mean midnight UTC; deadlines are moments shown as local time.

/** A month of the calendar; month is 1 for January. */
export type CalendarMonth = { year: number; month: number }

const MONTH_PATTERN = /^(\d{4})-(\d{2})$/
const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/

const monthFormat = new Intl.DateTimeFormat(undefined, { month: 'long', year: 'numeric' })
const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
const dateFormat = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'long', year: 'numeric' })
const timeFormat = new Intl.DateTimeFormat(undefined, { timeStyle: 'short' })

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** A plain date as a local Date at noon, so no time zone shift can move it to another day. */
function localDate(isoDate: string): Date | null {
  const parts = DATE_PATTERN.exec(isoDate)
  return parts === null ? null : new Date(Number(parts[1]), Number(parts[2]) - 1, Number(parts[3]), 12)
}

function isoDateOf(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function currentMonth(now: Date = new Date()): CalendarMonth {
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

/** "2026-10" as a month, or null when it is not a real month the calendar supports. */
export function parseMonth(value: string | null): CalendarMonth | null {
  const parts = value === null ? null : MONTH_PATTERN.exec(value)
  if (parts === null) {
    return null
  }
  const year = Number(parts[1])
  const month = Number(parts[2])
  return year >= 2000 && year <= 9999 && month >= 1 && month <= 12 ? { year, month } : null
}

export function monthKey({ year, month }: CalendarMonth): string {
  return `${year}-${pad(month)}`
}

export function addMonths({ year, month }: CalendarMonth, delta: number): CalendarMonth {
  const index = year * 12 + (month - 1) + delta
  return { year: Math.floor(index / 12), month: (index % 12) + 1 }
}

export function sameMonth(a: CalendarMonth, b: CalendarMonth): boolean {
  return a.year === b.year && a.month === b.month
}

/** The first and last day of the month, such as 2026-10-01 and 2026-10-31. */
export function monthRange({ year, month }: CalendarMonth): { from: string; to: string } {
  const lastDay = new Date(year, month, 0).getDate()
  return { from: `${year}-${pad(month)}-01`, to: `${year}-${pad(month)}-${pad(lastDay)}` }
}

/** "October 2026". */
export function monthLabel({ year, month }: CalendarMonth): string {
  return monthFormat.format(new Date(year, month - 1, 1, 12))
}

/** Today in the browser's time zone, such as 2026-10-20. */
export function todayIsoDate(now: Date = new Date()): string {
  return isoDateOf(now)
}

/** The local day a deadline falls on, such as 2026-10-20 for 2026-10-20T18:29:00Z in India. */
export function localDayOf(isoTime: string): string {
  const time = new Date(isoTime)
  return Number.isNaN(time.getTime()) ? isoTime.slice(0, 10) : isoDateOf(time)
}

/** "Tuesday 20 October", for a day heading. */
export function dayHeading(isoDate: string): string {
  const date = localDate(isoDate)
  return date === null ? isoDate : dayFormat.format(date)
}

/** "20 October 2026", or "20–25 October 2026" for an event over several days. */
export function dateRangeText(startDate: string, endDate: string): string {
  const start = localDate(startDate)
  const end = localDate(endDate)
  if (start === null || end === null) {
    return startDate === endDate ? startDate : `${startDate} – ${endDate}`
  }
  return startDate === endDate ? dateFormat.format(start) : dateFormat.formatRange(start, end)
}

/** The local time of a deadline, such as "11:59 pm". */
export function timeText(isoTime: string): string {
  const time = new Date(isoTime)
  return Number.isNaN(time.getTime()) ? isoTime : timeFormat.format(time)
}

/** The browser's IANA time zone, such as Asia/Kolkata. UTC only when the browser does not say. */
export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}
