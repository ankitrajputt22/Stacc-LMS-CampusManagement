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

export function monthRange({ year, month }: CalendarMonth): { from: string; to: string } {
  const lastDay = new Date(year, month, 0).getDate()
  return { from: `${year}-${pad(month)}-01`, to: `${year}-${pad(month)}-${pad(lastDay)}` }
}

export function monthLabel({ year, month }: CalendarMonth): string {
  return monthFormat.format(new Date(year, month - 1, 1, 12))
}

export function todayIsoDate(now: Date = new Date()): string {
  return isoDateOf(now)
}

export function localDayOf(isoTime: string): string {
  const time = new Date(isoTime)
  return Number.isNaN(time.getTime()) ? isoTime.slice(0, 10) : isoDateOf(time)
}

export function dayHeading(isoDate: string): string {
  const date = localDate(isoDate)
  return date === null ? isoDate : dayFormat.format(date)
}

export function dateRangeText(startDate: string, endDate: string): string {
  const start = localDate(startDate)
  const end = localDate(endDate)
  if (start === null || end === null) {
    return startDate === endDate ? startDate : `${startDate} – ${endDate}`
  }
  return startDate === endDate ? dateFormat.format(start) : dateFormat.formatRange(start, end)
}

export function timeText(isoTime: string): string {
  const time = new Date(isoTime)
  return Number.isNaN(time.getTime()) ? isoTime : timeFormat.format(time)
}

export function browserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  } catch {
    return 'UTC'
  }
}
