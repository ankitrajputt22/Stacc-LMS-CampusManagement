import { useCallback, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'

/** Where focus should go next. After a change it waits until the refreshed content is on screen. */
export type PendingFocus = { keys: string[]; afterRefresh: boolean }

/** The outcome of one change: its answer, or why it was refused. */
export type ChangeResult<T> =
  | { ok: true; data: T }
  | { ok: false; message: string | null; fieldErrors: Record<string, string> }

type ChangeOptions<T> = {
  /** Read out to screen readers once the change is saved. */
  done: string
  /** Where focus should go once the refreshed content is on screen, best choice first. */
  focus?: (data: T) => string[]
  /** Show a refusal in the editor's shared message area. Forms show their own instead. */
  showError?: boolean
}

const REFRESHED = 'The list has been refreshed.'

function refusalMessage(status: number | null, apiMessage: string | null, hasFieldErrors: boolean): string | null {
  switch (status) {
    case 400:
      // Field errors are shown next to their fields. A bare 400 here means the content changed meanwhile.
      return hasFieldErrors ? null : `The course content changed in the meantime. ${REFRESHED} Try again.`
    case 403:
      return 'You don’t have permission to change this course’s content. If your access changed recently, sign out and sign in again.'
    case 404:
      return `This content is no longer available. ${REFRESHED}`
    case 409:
      return `${apiMessage ?? 'This content can no longer be changed.'} ${REFRESHED}`
    default:
      return 'Couldn’t save the change. Check your connection and try again.'
  }
}

/**
 * Runs the editor's changes one at a time. A second click while one is saving is ignored, so nothing
 * is sent twice. After every change, and after a refusal that means the list is out of date, the
 * content is quietly reloaded. A 403 never signs the user out; a 401 is left to the shared client.
 */
export function useContentChanges(reload: () => void) {
  const busyRef = useRef(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [announcement, setAnnouncement] = useState('')
  const [pendingFocus, setPendingFocus] = useState<PendingFocus | null>(null)

  const run = useCallback(
    async <T,>(change: () => Promise<T>, options: ChangeOptions<T>): Promise<ChangeResult<T> | null> => {
      if (busyRef.current) {
        return null
      }
      busyRef.current = true
      setBusy(true)
      setError(null)
      setAnnouncement('')

      try {
        const data = await change()
        setPendingFocus(options.focus ? { keys: options.focus(data), afterRefresh: true } : null)
        setAnnouncement(options.done)
        reload()
        return { ok: true, data }
      } catch (failure) {
        const status = getHttpStatus(failure)
        if (status === 401) {
          return null
        }
        const apiError = getApiError(failure)
        const fieldErrors = Object.fromEntries(
          (apiError?.fieldErrors ?? []).map((fieldError) => [fieldError.field, fieldError.message]),
        )
        const message = refusalMessage(status, apiError?.message ?? null, Object.keys(fieldErrors).length > 0)
        if ((status === 400 && Object.keys(fieldErrors).length === 0) || status === 404 || status === 409) {
          reload()
        }
        if (options.showError !== false && message) {
          setError(message)
        }
        return { ok: false, message, fieldErrors }
      } finally {
        busyRef.current = false
        setBusy(false)
      }
    },
    [reload],
  )

  const focusNext = useCallback((keys: string[]) => setPendingFocus({ keys, afterRefresh: false }), [])
  const focusHandled = useCallback(() => setPendingFocus(null), [])

  return { busy, error, announcement, run, pendingFocus, focusNext, focusHandled }
}
