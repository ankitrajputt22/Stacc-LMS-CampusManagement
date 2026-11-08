import { describe, expect, it } from 'vitest'
import { submissionClosedReason, submissionRulesText } from './submissionRules'

describe('submission rules', () => {
  it('says why no new attempt can be handed in, as the backend decided', () => {
    expect(submissionClosedReason('OPEN', null, null, 4)).toBeNull()
    expect(submissionClosedReason('OPEN', null, 2, 1)).toBeNull()
    expect(submissionClosedReason('OPEN', null, 2, 2)).toBe('You have used every attempt (2).')
    expect(submissionClosedReason('CLOSED', null, null, 0)).toBe('This assignment no longer takes work.')
    expect(submissionClosedReason('NOT_OPEN', null, null, 0)).toBe('Submissions are not open yet.')
    expect(submissionClosedReason('NOT_OPEN', '2026-11-10T05:00:00Z', null, 0)).toMatch(/^Submissions open /)
  })

  it('sums up the rules for faculty', () => {
    expect(submissionRulesText({ availableFrom: null, availableUntil: null, allowLateSubmissions: true, maxAttempts: null })).toBe(
      'Open from publication · late work accepted · unlimited attempts',
    )
    expect(submissionRulesText({ availableFrom: null, availableUntil: null, allowLateSubmissions: false, maxAttempts: 1 })).toBe(
      'Open from publication · no late work · 1 attempt',
    )
  })
})
