import { describe, expect, it } from 'vitest'
import { MARKS, markLabel } from './rollCallText'

describe('roll call text', () => {
  it('offers present, absent, and out, and never "not marked" as a choice', () => {
    expect(MARKS.map((mark) => mark.label)).toEqual(['Present', 'Absent', 'Out'])
    expect(markLabel('UNMARKED')).toBe('Not marked')
    expect(markLabel('OUT')).toBe('Out')
  })
})
