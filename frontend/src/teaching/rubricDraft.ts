import type { LmsRubric, LmsRubricInput } from '../types/lms'

export const MAX_CRITERIA = 20
export const MAX_LEVELS = 6

export type LevelDraft = { title: string; description: string; points: string }
export type CriterionDraft = { title: string; description: string; maxPoints: string; levels: LevelDraft[] }
export type RubricDraft = { title: string; description: string; criteria: CriterionDraft[] }

const POINTS = /^\d{1,6}(\.\d{1,2})?$/

export function emptyCriterion(): CriterionDraft {
  return { title: '', description: '', maxPoints: '', levels: [] }
}

export function emptyRubric(): RubricDraft {
  return { title: '', description: '', criteria: [emptyCriterion()] }
}

export function draftOfRubric(rubric: LmsRubric): RubricDraft {
  return {
    title: rubric.title,
    description: rubric.description ?? '',
    criteria: rubric.criteria.map((criterion) => ({
      title: criterion.title,
      description: criterion.description ?? '',
      maxPoints: String(criterion.maxPoints),
      levels: criterion.levels.map((level) => ({ title: level.title, description: level.description ?? '', points: String(level.points) })),
    })),
  }
}

/** The total of the criteria's maximum points typed so far, ignoring ones not yet valid. */
export function draftTotal(draft: RubricDraft): number {
  return draft.criteria.reduce((sum, criterion) => sum + (POINTS.test(criterion.maxPoints.trim()) ? Number(criterion.maxPoints) : 0), 0)
}

/**
 * Checks the rubric the way the backend does and, when it fits, turns it into what the API is sent. Messages name the
 * criterion and level by number.
 */
export function checkRubric(draft: RubricDraft): { errors: string[]; input: LmsRubricInput | null } {
  const errors: string[] = []
  if (draft.title.trim() === '') {
    errors.push('Enter a title.')
  }
  if (draft.criteria.length === 0 || draft.criteria.length > MAX_CRITERIA) {
    errors.push(`Add 1 to ${MAX_CRITERIA} criteria.`)
  }
  const titles = new Set<string>()
  draft.criteria.forEach((criterion, index) => {
    const name = `Criterion ${index + 1}`
    if (criterion.title.trim() === '') {
      errors.push(`${name}: enter a title.`)
    } else if (titles.has(criterion.title.trim().toLowerCase())) {
      errors.push(`${name}: use a title no other criterion has.`)
    }
    titles.add(criterion.title.trim().toLowerCase())
    const max = criterion.maxPoints.trim()
    if (!POINTS.test(max) || Number(max) <= 0) {
      errors.push(`${name}: enter maximum points above 0, with at most 2 decimal places.`)
    }
    criterion.levels.forEach((level, levelIndex) => {
      const levelName = `${name}, level ${levelIndex + 1}`
      if (level.title.trim() === '') {
        errors.push(`${levelName}: enter a title.`)
      }
      if (!POINTS.test(level.points.trim())) {
        errors.push(`${levelName}: enter points from 0, with at most 2 decimal places.`)
      } else if (POINTS.test(max) && Number(level.points) > Number(max)) {
        errors.push(`${levelName}: the points cannot be above the criterion's maximum.`)
      }
    })
  })
  if (errors.length > 0) {
    return { errors, input: null }
  }
  return {
    errors,
    input: {
      title: draft.title.trim(),
      description: draft.description.trim() === '' ? null : draft.description.trim(),
      criteria: draft.criteria.map((criterion) => ({
        title: criterion.title.trim(),
        description: criterion.description.trim() === '' ? null : criterion.description.trim(),
        maxPoints: Number(criterion.maxPoints),
        levels: criterion.levels.map((level) => ({
          title: level.title.trim(),
          description: level.description.trim() === '' ? null : level.description.trim(),
          points: Number(level.points),
        })),
      })),
    },
  }
}
