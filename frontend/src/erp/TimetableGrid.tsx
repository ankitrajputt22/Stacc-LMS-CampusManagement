import type { DayOfWeek, TimetableSlot } from '../types/erp'
import { hourRange, label, slotsAt } from './timetable'

const DAYS: { day: DayOfWeek; short: string; name: string }[] = [
  { day: 'MONDAY', short: 'Mon', name: 'Monday' },
  { day: 'TUESDAY', short: 'Tue', name: 'Tuesday' },
  { day: 'WEDNESDAY', short: 'Wed', name: 'Wednesday' },
  { day: 'THURSDAY', short: 'Thu', name: 'Thursday' },
  { day: 'FRIDAY', short: 'Fri', name: 'Friday' },
  { day: 'SATURDAY', short: 'Sat', name: 'Saturday' },
  { day: 'SUNDAY', short: 'Sun', name: 'Sunday' },
]

const KIND_WORDS = { LECTURE: 'Lecture', TUTORIAL: 'Tutorial', LAB: 'Lab' } as const
const LUNCH_HOUR = 13

export function TimetableGrid({ slots, caption }: { slots: readonly TimetableSlot[]; caption: string }) {
  const days = DAYS.filter((entry) => entry.day !== 'SUNDAY' || slots.some((slot) => slot.day === 'SUNDAY'))
  const hours = hourRange(slots)
  const lunchFree = hours.includes(LUNCH_HOUR) && days.every((entry) => slotsAt(slots, entry.day, LUNCH_HOUR).length === 0)
  return (
    <div className="overflow-x-auto rounded-panel border border-line">
      <table className="w-full min-w-[52rem] table-fixed border-collapse text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-surface-muted">
          <tr>
            <th scope="col" className="w-36 border-b border-line px-4 py-3 text-[0.95rem] font-semibold text-ink">Time</th>
            {days.map((entry) => (
              <th key={entry.day} scope="col" className="border-b border-l border-line px-2 py-3 text-center text-[0.95rem] font-semibold text-ink">
                <abbr title={entry.name} className="no-underline">{entry.short}</abbr>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {hours.map((hour) =>
            hour === LUNCH_HOUR && lunchFree ? (
              <tr key={hour} className="bg-surface-muted">
                <th scope="row" className="border-b border-line px-4 py-2.5 font-normal whitespace-nowrap text-ink">
                  {label(hour)} – {label(hour + 1)}
                </th>
                <td colSpan={days.length} className="border-b border-l border-line py-2.5 text-center font-semibold text-ink">
                  Lunch Break
                </td>
              </tr>
            ) : (
              <tr key={hour}>
                <th scope="row" className="border-b border-line px-4 py-2 align-middle font-normal whitespace-nowrap text-ink">
                  {label(hour)} – {label(hour + 1)}
                </th>
                {days.map((entry) => (
                  <td key={entry.day} className="h-14 border-b border-l border-line p-1 align-top">
                    {slotsAt(slots, entry.day, hour).map((slot) => (
                      <div key={slot.slotId} className="mb-1 rounded-md border-l-4 border-teal-700 bg-sky-100 px-2 py-1 last:mb-0">
                        <p className="font-semibold text-ink">
                          {slot.courseCode}
                          {slot.sectionCode && <span className="font-normal text-ink-muted"> · {slot.sectionCode}</span>}
                        </p>
                        <p className="text-xs text-ink-muted">
                          {slot.room} · {KIND_WORDS[slot.kind]}
                        </p>
                      </div>
                    ))}
                  </td>
                ))}
              </tr>
            ),
          )}
        </tbody>
      </table>
    </div>
  )
}
