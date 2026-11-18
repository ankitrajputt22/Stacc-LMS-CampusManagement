import type { RollCallMark } from '../../types/hostel'

export const MARKS: { value: Exclude<RollCallMark, 'UNMARKED'>; label: string }[] = [
  { value: 'PRESENT', label: 'Present' },
  { value: 'ABSENT', label: 'Absent' },
  { value: 'OUT', label: 'Out' },
]

export function markLabel(mark: RollCallMark): string {
  return mark === 'UNMARKED' ? 'Not marked' : (MARKS.find((choice) => choice.value === mark)?.label ?? mark)
}
