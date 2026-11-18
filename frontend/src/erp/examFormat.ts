export function hhmm(time: string): string {
  return time.slice(0, 5)
}

export function plain(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const number = Number(value)
  return Number.isInteger(number) ? String(number) : String(Number(number.toFixed(2)))
}

export function gpa(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : Number(value).toFixed(2)
}
