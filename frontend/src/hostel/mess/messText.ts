import type { Meal } from '../../types/hostel'

export const MEALS: { value: Meal; label: string }[] = [
  { value: 'BREAKFAST', label: 'Breakfast' },
  { value: 'LUNCH', label: 'Lunch' },
  { value: 'SNACKS', label: 'Snacks' },
  { value: 'DINNER', label: 'Dinner' },
]

export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

export function mealLabel(meal: Meal): string {
  return MEALS.find((choice) => choice.value === meal)?.label ?? meal
}

/** "Thu 12 Nov" for a yyyy-mm-dd date, read as a calendar date (no time zone shift). */
export function dayLabel(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' })
}

/** Whether a meal on this date can still be rated: today or the last seven days. */
export function canRate(date: string, today: string): boolean {
  const [y1, m1, d1] = date.split('-').map(Number)
  const [y2, m2, d2] = today.split('-').map(Number)
  const days = Math.round((Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86_400_000)
  return days >= 0 && days <= 7
}
