import type { FormOutcome } from './ContentForm'
import type { ChangeResult, useContentChanges } from './useContentChanges'

export const TITLE_MAX = 200
export const DESCRIPTION_MAX = 2000
export const CONTENT_MAX = 20_000
export const MATERIAL_DESCRIPTION_MAX = 1000
export const ANNOUNCEMENT_MESSAGE_MAX = 10_000
export const ASSIGNMENT_INSTRUCTIONS_MAX = 20_000

export const ASSIGNMENTS_SECTION_ID = 'assignments'

export function submissionsPath(lmsCourseId: number, assignmentId: number): string {
  return `/teaching/${lmsCourseId}/assignments/${assignmentId}/submissions`
}

export type OpenForm =
  | { type: 'addModule'; returnFocus: string }
  | { type: 'editModule'; id: number }
  | { type: 'addTopic'; moduleId: number }
  | { type: 'editTopic'; id: number }
  | { type: 'addMaterial'; topicId: number }
  | { type: 'editMaterial'; id: number }

export type Confirming = { kind: 'module' | 'topic' | 'material'; id: number }

export type Changes = ReturnType<typeof useContentChanges>

export function asOutcome<T>(result: ChangeResult<T> | null): FormOutcome {
  if (result === null) {
    return { ok: false, message: null, fieldErrors: {} }
  }
  return result.ok ? { ok: true } : result
}

export function moved(ids: number[], from: number, direction: -1 | 1): number[] {
  const next = [...ids]
  const [item] = next.splice(from, 1)
  next.splice(from + direction, 0, item)
  return next
}

export function srContext(text: string) {
  return <span className="sr-only"> {text}</span>
}
