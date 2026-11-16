import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { TimetableSlot } from '../types/erp'
import { TimetableGrid } from './TimetableGrid'
import { hourRange, slotsAt } from './timetable'

function slot(courseCode: string, day: TimetableSlot['day'], startsAt: string, endsAt: string): TimetableSlot {
  return { slotId: Math.random(), courseOfferingId: 1, courseCode, courseName: courseCode, sectionCode: null, day, startsAt, endsAt, room: 'LAB-02', kind: 'LAB' }
}

describe('timetable', () => {
  it('shows 09:00 to 17:00, stretched for an early class', () => {
    expect(hourRange([])).toEqual([9, 10, 11, 12, 13, 14, 15, 16])
    expect(hourRange([slot('CS305', 'MONDAY', '08:00:00', '09:00:00')])[0]).toBe(8)
  })

  it('puts a two-hour lab in both of its hours and nowhere else', () => {
    const lab = slot('CS305', 'TUESDAY', '14:00:00', '16:00:00')
    expect(slotsAt([lab], 'TUESDAY', 14)).toHaveLength(1)
    expect(slotsAt([lab], 'TUESDAY', 15)).toHaveLength(1)
    expect(slotsAt([lab], 'TUESDAY', 16)).toHaveLength(0)
    expect(slotsAt([lab], 'MONDAY', 14)).toHaveLength(0)
  })

  it('marks a free lunch hour and names each class with its room and kind', () => {
    render(<TimetableGrid caption="Your weekly classes" slots={[slot('CS305', 'TUESDAY', '14:00:00', '16:00:00')]} />)
    const table = screen.getByRole('table', { name: 'Your weekly classes' })
    expect(within(table).getByText('Lunch Break')).toBeInTheDocument()
    expect(within(table).getAllByText('LAB-02 · Lab')).toHaveLength(2)
  })
})
