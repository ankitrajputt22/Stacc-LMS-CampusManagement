import { describe, expect, it } from 'vitest'
import { alignmentText, bloomLabel, outcomeCodes } from './bloomText'

const outcome = (code: string) => ({ id: 1, code, description: 'd', status: 'ACTIVE' as const })

describe('bloomText', () => {
  it('names Bloom levels and outcome codes in words', () => {
    expect(bloomLabel('ANALYZE')).toBe('Analyze')
    expect(outcomeCodes([])).toBeNull()
    expect(outcomeCodes([outcome('CO1'), outcome('CO3')])).toBe('CO1, CO3')
  })

  it('says what an assignment is aligned with, or that it is not', () => {
    expect(alignmentText({ bloomLevel: null, outcomes: [] })).toBe('Not aligned with outcomes yet.')
    expect(alignmentText({ bloomLevel: 'APPLY', outcomes: [outcome('CO1')] })).toBe('Outcomes CO1 · Bloom level Apply')
    expect(alignmentText({ bloomLevel: 'CREATE', outcomes: [] })).toBe('Bloom level Create')
  })
})
