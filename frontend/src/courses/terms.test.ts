import { describe, expect, it } from 'vitest'
import { groupByTerm } from './terms'

const course = (code: string, session: string, semester: number, program = 'BTECH') => ({
  code,
  academicSessionCode: session,
  programCode: program,
  semesterNumber: semester,
})

describe('groupByTerm', () => {
  it('puts the latest session first and keeps the backend order within a term', () => {
    const terms = groupByTerm([
      course('CS201', '2025-26', 2),
      course('CS302', '2026-27', 3),
      course('CS301', '2026-27', 3),
      course('CS401', '2026-27', 4),
    ])

    expect(terms.map((term) => term.heading)).toEqual([
      '2026-27 · Semester 3',
      '2026-27 · Semester 4',
      '2025-26 · Semester 2',
    ])
    expect(terms[0].items.map((item) => item.code)).toEqual(['CS302', 'CS301'])
  })

  it('keeps programs apart and gives nothing for no courses', () => {
    const terms = groupByTerm([course('A1', '2026-27', 1, 'MCA'), course('B1', '2026-27', 1, 'BTECH')])

    expect(terms.map((term) => term.programCode)).toEqual(['BTECH', 'MCA'])
    expect(groupByTerm([])).toEqual([])
  })
})
