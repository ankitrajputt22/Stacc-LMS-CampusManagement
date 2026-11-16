/** "10:00:00" as "10:00". */
export function hhmm(time: string): string {
  return time.slice(0, 5)
}

/** A number of marks or points without needless decimals: 84, 8.25, 76.5. */
export function plain(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  const number = Number(value)
  return Number.isInteger(number) ? String(number) : String(Number(number.toFixed(2)))
}

/** SGPA and CGPA always with two decimals, as the backend rounds them. */
export function gpa(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : Number(value).toFixed(2)
}
