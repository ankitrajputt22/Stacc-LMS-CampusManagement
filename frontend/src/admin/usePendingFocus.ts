import { type RefObject, useCallback, useEffect, useRef } from 'react'

/**
 * Moves focus to the visible element marked with `data-focus-key` once it is on screen, for example after a list
 * reloads and the button that had focus is gone. Call the returned function together with the change that removes
 * the focused element; the key is tried after each render until its element appears.
 */
export function usePendingFocus(container: RefObject<HTMLElement | null>) {
  const pending = useRef<string | null>(null)

  useEffect(() => {
    const key = pending.current
    if (key === null) {
      return
    }
    const target = [...(container.current?.querySelectorAll<HTMLElement>(`[data-focus-key="${key}"]`) ?? [])].find(
      (element) => element.offsetParent !== null,
    )
    if (target) {
      target.focus()
      pending.current = null
    }
  })

  return useCallback((key: string) => {
    pending.current = key
  }, [])
}
