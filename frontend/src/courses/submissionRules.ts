import { formatDateTime } from './dates'

/** Why no new attempt can be handed in now, or null when one can. */
export function submissionClosedReason(
  window: 'NOT_OPEN' | 'OPEN' | 'CLOSED',
  availableFrom: string | null,
  maxAttempts: number | null,
  attemptsMade: number,
): string | null {
  if (window === 'NOT_OPEN') {
    return availableFrom ? `Submissions open ${formatDateTime(availableFrom)}.` : 'Submissions are not open yet.'
  }
  if (window === 'CLOSED') {
    return 'This assignment no longer takes work.'
  }
  if (maxAttempts !== null && attemptsMade >= maxAttempts) {
    return `You have used every attempt (${maxAttempts}).`
  }
  return null
}

/** An assignment's submission rules in a few words, for faculty lists. */
export function submissionRulesText(rules: {
  availableFrom: string | null
  availableUntil: string | null
  allowLateSubmissions: boolean
  maxAttempts: number | null
}): string {
  return [
    rules.availableFrom ? `Opens ${formatDateTime(rules.availableFrom)}` : 'Open from publication',
    !rules.allowLateSubmissions
      ? 'no late work'
      : rules.availableUntil
        ? `late work until ${formatDateTime(rules.availableUntil)}`
        : 'late work accepted',
    rules.maxAttempts === null ? 'unlimited attempts' : `${rules.maxAttempts} ${rules.maxAttempts === 1 ? 'attempt' : 'attempts'}`,
  ].join(' · ')
}
