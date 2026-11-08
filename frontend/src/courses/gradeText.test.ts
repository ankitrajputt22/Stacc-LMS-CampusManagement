import { describe, expect, it } from 'vitest'
import { cellText, gradeTypeLabel, matchesStudent } from './gradeText'

describe('grade text', () => {
  it('shows marks, or why there are none', () => {
    expect(cellText({ marks: 17, status: 'GRADED' })).toBe('17')
    expect(cellText({ marks: null, status: 'NEEDS_GRADING' })).toBe('Needs grading')
    expect(cellText({ marks: null, status: 'NO_MARKS' })).toBe('—')
    expect(gradeTypeLabel('MANUAL')).toBe('Other')
  })

  it('finds students by part of their college ID', () => {
    expect(matchesStudent('2408400100011', ' 0011 ')).toBe(true)
    expect(matchesStudent('student1', 'STUDENT')).toBe(true)
    expect(matchesStudent('student1', 'faculty')).toBe(false)
  })
})
