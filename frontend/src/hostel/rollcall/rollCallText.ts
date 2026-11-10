import type { RollCallMark } from '../../types/hostel'

/** The marks a warden can give, in the order they are offered. "Not marked" is never one of them. */
export const MARKS: { value: Exclude<RollCallMark, 'UNMARKED'>; label: string }[] = [
  { value: 'PRESENT', label: 'Present' },
  { value: 'ABSENT', label: 'Absent' },
  { value: 'OUT', label: 'Out' },
]

export function markLabel(mark: RollCallMark): string {
  return mark === 'UNMARKED' ? 'Not marked' : (MARKS.find((choice) => choice.value === mark)?.label ?? mark)
}
