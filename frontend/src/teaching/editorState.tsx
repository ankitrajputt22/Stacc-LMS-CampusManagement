import type { FormOutcome } from './ContentForm'
import type { ChangeResult, useContentChanges } from './useContentChanges'

// The limits the backend enforces, so the forms can say so before sending.
export const TITLE_MAX = 200
export const DESCRIPTION_MAX = 2000
export const CONTENT_MAX = 20_000
export const MATERIAL_DESCRIPTION_MAX = 1000
export const ANNOUNCEMENT_MESSAGE_MAX = 10_000

/** The one form open in the editor at a time. */
export type OpenForm =
  | { type: 'addModule'; returnFocus: string }
  | { type: 'editModule'; id: number }
  | { type: 'addTopic'; moduleId: number }
  | { type: 'editTopic'; id: number }
  | { type: 'addMaterial'; topicId: number }
  | { type: 'editMaterial'; id: number }

/** The one archive waiting for confirmation, if any. */
export type Confirming = { kind: 'module' | 'topic' | 'material'; id: number }

export type Changes = ReturnType<typeof useContentChanges>

export function asOutcome<T>(result: ChangeResult<T> | null): FormOutcome {
  if (result === null) {
    // Ignored because another change was saving, or the session ended: nothing to show in the form.
    return { ok: false, message: null, fieldErrors: {} }
  }
  return result.ok ? { ok: true } : result
}

/** The ids in a new order: the item at `from` moved one place up or down. */
export function moved(ids: number[], from: number, direction: -1 | 1): number[] {
  const next = [...ids]
  const [item] = next.splice(from, 1)
  next.splice(from + direction, 0, item)
  return next
}

/** Words for screen readers that say which item a repeated button belongs to. */
export function srContext(text: string) {
  return <span className="sr-only"> {text}</span>
}
