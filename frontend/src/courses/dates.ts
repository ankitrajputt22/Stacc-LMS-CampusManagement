const dateTimeFormat = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

export function formatDateTime(isoTime: string): string {
  const time = new Date(isoTime)
  return Number.isNaN(time.getTime()) ? isoTime : dateTimeFormat.format(time)
}

export function isPast(isoTime: string, now: number = Date.now()): boolean {
  const time = new Date(isoTime).getTime()
  return !Number.isNaN(time) && time < now
}

const LOCAL_INPUT_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export function toLocalInputValue(isoTime: string): string {
  const time = new Date(isoTime)
  if (Number.isNaN(time.getTime())) {
    return ''
  }
  return `${time.getFullYear()}-${pad(time.getMonth() + 1)}-${pad(time.getDate())}T${pad(time.getHours())}:${pad(time.getMinutes())}`
}

export function fromLocalInputValue(value: string): Date | null {
  const parts = LOCAL_INPUT_PATTERN.exec(value)
  if (parts === null) {
    return null
  }
  const [year, month, day, hours, minutes, seconds] = parts.slice(1).map((part) => Number(part ?? 0))
  const time = new Date(year, month - 1, day, hours, minutes, seconds)
  return Number.isNaN(time.getTime()) || time.getMonth() !== month - 1 || time.getDate() !== day ? null : time
}

export function localTimeZoneName(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone
}
