import type { BloomLevel, LmsOutcome } from '../types/lms'

export const BLOOM_LEVELS: { value: BloomLevel; label: string }[] = [
  { value: 'REMEMBER', label: 'Remember' },
  { value: 'UNDERSTAND', label: 'Understand' },
  { value: 'APPLY', label: 'Apply' },
  { value: 'ANALYZE', label: 'Analyze' },
  { value: 'EVALUATE', label: 'Evaluate' },
  { value: 'CREATE', label: 'Create' },
]

export function bloomLabel(level: BloomLevel): string {
  return BLOOM_LEVELS.find((candidate) => candidate.value === level)?.label ?? level
}

export function outcomeCodes(outcomes: { code: string }[]): string | null {
  return outcomes.length === 0 ? null : outcomes.map((outcome) => outcome.code).join(', ')
}

export function alignmentText(assignment: { bloomLevel: BloomLevel | null; outcomes: LmsOutcome[] }): string {
  const codes = outcomeCodes(assignment.outcomes)
  const parts = [codes && `Outcomes ${codes}`, assignment.bloomLevel && `Bloom level ${bloomLabel(assignment.bloomLevel)}`]
    .filter((part): part is string => Boolean(part))
  return parts.length === 0 ? 'Not aligned with outcomes yet.' : parts.join(' · ')
}
