import type { DayOfWeek, TimetableSlot } from '../types/erp'

export function minutes(time: string): number {
  const [hours, mins] = time.split(':').map(Number)
  return hours * 60 + mins
}

export function label(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`
}

/** The hours the grid shows: 09:00 to 17:00, stretched to fit any class outside them. */
export function hourRange(slots: readonly TimetableSlot[]): number[] {
  const first = Math.min(9, ...slots.map((slot) => Math.floor(minutes(slot.startsAt) / 60)))
  const last = Math.max(17, ...slots.map((slot) => Math.ceil(minutes(slot.endsAt) / 60)))
  return Array.from({ length: last - first }, (_, index) => first + index)
}

/** The classes that take place during this hour of this day. */
export function slotsAt(slots: readonly TimetableSlot[], day: DayOfWeek, hour: number): TimetableSlot[] {
  return slots.filter((slot) => slot.day === day && minutes(slot.startsAt) < (hour + 1) * 60 && minutes(slot.endsAt) > hour * 60)
}

